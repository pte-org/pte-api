package com.pte.scoring.internal.service.ai;

import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.dto.response.AiScoreDetailResponse;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AiScoreDetailQueryServiceTest {

    private final UUID tenantId = UUID.randomUUID();

    @Mock
    private AiEvaluationRepository repository;

    private AiScoreDetailQueryService service;

    @BeforeEach
    void setUp() {
        service = new AiScoreDetailQueryService(repository, JsonMapper.builder().findAndAddModules().build());
    }

    @Test
    void aScoredAnswerCarriesCriteriaFeedbackTranscriptDetailAndTheFallbackFlag() {
        AiEvaluation answer = aiAnswer(ScoringAnswerStatus.SCORED, 72);
        answer.recordDetail("{\"CONTENT\":80,\"FLUENCY\":64}", "Good range.", "the transcript",
                "{\"confidence\":\"LOW\",\"errors\":[\"x\"]}", true);

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
        AiEvaluation answer = aiAnswer(ScoringAnswerStatus.SCORING_FAILED, 55);
        answer.recordDetail("{\"CONTENT\":50}", "old", null, "{}", false);
        answer.recordFailure(AiFailureReason.PROVIDER_RATE_LIMITED);

        AiScoreDetailResponse ai = service.find(answer);

        assertThat(ai.status()).isEqualTo("FAILED");
        assertThat(ai.score()).isNull();
        assertThat(ai.failureReason()).isEqualTo("PROVIDER_RATE_LIMITED");
    }

    @Test
    void anAnswerStillBeingScoredHasAStatusEvenBeforeAnyAiOutcomeExists() {
        AiEvaluation answer = evaluation();
        answer.setStatus(ScoringAnswerStatus.AI_SCORING);

        AiScoreDetailResponse ai = service.find(answer);

        assertThat(ai.status()).isEqualTo("SCORING");
        assertThat(ai.score()).isNull();
        assertThat(ai.criteria()).isEmpty();
        assertThat(ai.detail()).isEmpty();
    }

    @Test
    void anObjectiveAnswerHasNoAiDetailAtAll() {
        AiEvaluation answer = evaluation();
        answer.markScored(100, java.time.Instant.now());

        assertThat(service.find(answer)).isNull();
    }

    @Test
    void unreadableStoredJsonDegradesToEmptyMapsInsteadOfFailingTheScreen() {
        AiEvaluation answer = aiAnswer(ScoringAnswerStatus.SCORED, 60);
        answer.recordDetail("not json", "ok", null, "{broken", false);

        AiScoreDetailResponse ai = service.find(answer);

        assertThat(ai.criteria()).isEmpty();
        assertThat(ai.detail()).isEmpty();
        assertThat(ai.feedback()).isEqualTo("ok");
    }

    @Test
    void severalAnswersAreMappedWithoutAnyLookupAndObjectiveOnesAreSkipped() {
        AiEvaluation scored = aiAnswer(ScoringAnswerStatus.SCORED, 80);
        scored.recordDetail("{\"CONTENT\":80}", "fine", null, "{}", false);
        AiEvaluation scoring = evaluation();
        scoring.setStatus(ScoringAnswerStatus.AI_SCORING);
        AiEvaluation objective = evaluation();
        objective.markScored(100, java.time.Instant.now());

        Map<UUID, AiScoreDetailResponse> byAnswer = service.findAll(tenantId, List.of(scored, scoring, objective));

        assertThat(byAnswer).containsOnlyKeys(scored.getAnswerPublicId(), scoring.getAnswerPublicId());
        assertThat(byAnswer.get(scored.getAnswerPublicId()).criteria()).containsEntry("CONTENT", 80);
        assertThat(byAnswer.get(scoring.getAnswerPublicId()).status()).isEqualTo("SCORING");
    }

    private AiEvaluation evaluation() {
        return new AiEvaluation(tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "READ_ALOUD", "SPEAKING", ScoringMethod.AI_SPEECH);
    }

    /** An answer an AI provider has scored, as the worker leaves it. */
    private AiEvaluation aiAnswer(ScoringAnswerStatus status, int rawScore) {
        AiEvaluation answer = evaluation();
        answer.markAiScored(rawScore, AiProviderCategory.REAL, "provider", "model", "v1", java.time.Instant.now());
        answer.setStatus(status);
        return answer;
    }
}
