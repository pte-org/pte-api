package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.ScoringAnswerAiDetail;
import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.dto.response.AiScoreDetailResponse;
import com.pte.scoring.internal.repository.ScoringAnswerAiDetailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiScoreDetailQueryServiceTest {

    private final UUID tenantId = UUID.randomUUID();

    @Mock
    private ScoringAnswerAiDetailRepository repository;

    private AiScoreDetailQueryService service;

    @BeforeEach
    void setUp() {
        service = new AiScoreDetailQueryService(repository, JsonMapper.builder().findAndAddModules().build());
    }

    @Test
    void aScoredAnswerCarriesCriteriaFeedbackTranscriptDetailAndTheFallbackFlag() {
        ScoringAnswer answer = answer(ScoringAnswerStatus.SCORED, 72);
        ScoringAnswerAiDetail row = new ScoringAnswerAiDetail(answer.getAnswerPublicId(), tenantId);
        row.recordResult("{\"CONTENT\":80,\"FLUENCY\":64}", "Good range.", "the transcript",
                "{\"confidence\":\"LOW\",\"errors\":[\"x\"]}", true);
        when(repository.findByAnswerPublicIdAndTenantId(answer.getAnswerPublicId(), tenantId))
                .thenReturn(Optional.of(row));

        AiScoreDetailResponse ai = service.find(answer);

        assertThat(ai.status()).isEqualTo("SCORED");
        assertThat(ai.score()).isEqualTo(72);
        assertThat(ai.criteria()).containsEntry("CONTENT", 80).containsEntry("FLUENCY", 64);
        assertThat(ai.feedback()).isEqualTo("Good range.");
        assertThat(ai.transcript()).isEqualTo("the transcript");
        assertThat(ai.detail()).containsEntry("confidence", "LOW");
        assertThat(ai.fallback()).isTrue();
        assertThat(ai.failureReason()).isNull();
    }

    @Test
    void aFailedAnswerShowsTheReasonCodeAndNoScoreEvenWhenAnOlderResultIsKept() {
        ScoringAnswer answer = answer(ScoringAnswerStatus.SCORING_FAILED, 55);
        ScoringAnswerAiDetail row = new ScoringAnswerAiDetail(answer.getAnswerPublicId(), tenantId);
        row.recordResult("{\"CONTENT\":50}", "old", null, "{}", false);
        row.recordFailure(AiFailureReason.PROVIDER_RATE_LIMITED);
        when(repository.findByAnswerPublicIdAndTenantId(answer.getAnswerPublicId(), tenantId))
                .thenReturn(Optional.of(row));

        AiScoreDetailResponse ai = service.find(answer);

        assertThat(ai.status()).isEqualTo("FAILED");
        assertThat(ai.score()).isNull();
        assertThat(ai.failureReason()).isEqualTo("PROVIDER_RATE_LIMITED");
    }

    @Test
    void anAnswerStillBeingScoredHasAStatusEvenBeforeAnyDetailRowExists() {
        ScoringAnswer answer = answer(ScoringAnswerStatus.AI_SCORING, null);
        when(repository.findByAnswerPublicIdAndTenantId(answer.getAnswerPublicId(), tenantId))
                .thenReturn(Optional.empty());

        AiScoreDetailResponse ai = service.find(answer);

        assertThat(ai.status()).isEqualTo("SCORING");
        assertThat(ai.score()).isNull();
        assertThat(ai.criteria()).isEmpty();
        assertThat(ai.detail()).isEmpty();
    }

    @Test
    void anAnswerWithNoAiRowAndNoAiStatusHasNoAiDetailAtAll() {
        ScoringAnswer answer = answer(ScoringAnswerStatus.SCORED, 100);
        when(repository.findByAnswerPublicIdAndTenantId(answer.getAnswerPublicId(), tenantId))
                .thenReturn(Optional.empty());

        assertThat(service.find(answer)).isNull();
    }

    @Test
    void lookupsAreKeyedByTheAnswersOwnTenantNeverAClientValue() {
        ScoringAnswer answer = answer(ScoringAnswerStatus.SCORED, 70);
        when(repository.findByAnswerPublicIdAndTenantId(answer.getAnswerPublicId(), tenantId))
                .thenReturn(Optional.empty());

        service.find(answer);

        verify(repository).findByAnswerPublicIdAndTenantId(answer.getAnswerPublicId(), tenantId);
    }

    @Test
    void unreadableStoredJsonDegradesToEmptyMapsInsteadOfFailingTheScreen() {
        ScoringAnswer answer = answer(ScoringAnswerStatus.SCORED, 60);
        ScoringAnswerAiDetail row = new ScoringAnswerAiDetail(answer.getAnswerPublicId(), tenantId);
        row.recordResult("not json", "ok", null, "{broken", false);
        when(repository.findByAnswerPublicIdAndTenantId(answer.getAnswerPublicId(), tenantId))
                .thenReturn(Optional.of(row));

        AiScoreDetailResponse ai = service.find(answer);

        assertThat(ai.criteria()).isEmpty();
        assertThat(ai.detail()).isEmpty();
        assertThat(ai.feedback()).isEqualTo("ok");
    }

    @Test
    void severalAnswersAreLoadedWithOneTenantScopedQuery() {
        ScoringAnswer scored = answer(ScoringAnswerStatus.SCORED, 80);
        ScoringAnswer scoring = answer(ScoringAnswerStatus.AI_SCORING, null);
        ScoringAnswer objective = answer(ScoringAnswerStatus.SCORED, 100);
        ScoringAnswerAiDetail row = new ScoringAnswerAiDetail(scored.getAnswerPublicId(), tenantId);
        row.recordResult("{\"CONTENT\":80}", "fine", null, "{}", false);
        when(repository.findByTenantIdAndAnswerPublicIdIn(tenantId,
                List.of(scored.getAnswerPublicId(), scoring.getAnswerPublicId(), objective.getAnswerPublicId())))
                .thenReturn(List.of(row));

        Map<UUID, AiScoreDetailResponse> byAnswer = service.findAll(tenantId, List.of(scored, scoring, objective));

        assertThat(byAnswer).containsOnlyKeys(scored.getAnswerPublicId(), scoring.getAnswerPublicId());
        assertThat(byAnswer.get(scored.getAnswerPublicId()).criteria()).containsEntry("CONTENT", 80);
        assertThat(byAnswer.get(scoring.getAnswerPublicId()).status()).isEqualTo("SCORING");
    }

    private ScoringAnswer answer(ScoringAnswerStatus status, Integer rawScore) {
        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(UUID.randomUUID());
        answer.setTenantId(tenantId);
        answer.setStatus(status);
        answer.setRawScore(rawScore);
        return answer;
    }
}
