package com.pte.scoring.internal.service;

import com.pte.media.MediaService;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.dto.response.AiScoreDetailResponse;
import com.pte.scoring.internal.dto.response.AnswerPayloadKind;
import com.pte.scoring.internal.dto.response.AnswerReviewDetailResponse;
import com.pte.scoring.internal.dto.response.DecodedAnswerPayload;
import com.pte.scoring.internal.exception.AnswerNotFoundException;
import com.pte.scoring.internal.repository.AiDetailFlags;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Host answer review: the AI side (criteria, feedback, transcript) rides along with the existing detail and list. */
@ExtendWith(MockitoExtension.class)
class ScoringReviewAiDetailTest {

    private final UUID tenantId = UUID.randomUUID();

    @Mock
    private ScoringAnswerRepository answerRepository;
    @Mock
    private AnswerPayloadDecoder decoder;
    @Mock
    private MediaService mediaService;
    @Mock
    private AiScoreDetailQueryService aiDetails;
    @Mock
    private ScoringEligibilityQueryService eligibility;

    private ScoringReviewService service;
    private CurrentUser host;

    @BeforeEach
    void setUp() {
        service = new ScoringReviewService(answerRepository, decoder, mediaService, null, null, null, aiDetails,
                eligibility);
        host = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
    }

    @Test
    void detailIncludesTheAiScoreAndItsBreakdown() {
        ScoringAnswer answer = answer(tenantId);
        AiScoreDetailResponse ai = new AiScoreDetailResponse("SCORED", 72, Map.of("CONTENT", 80), "Good.",
                "spoken words", Map.of(), false, null);
        when(answerRepository.findByAnswerPublicId(answer.getAnswerPublicId())).thenReturn(Optional.of(answer));
        when(decoder.decode(answer)).thenReturn(new DecodedAnswerPayload(
                AnswerPayloadKind.TEXT, "x", null, null, null, null, null));
        when(aiDetails.find(answer)).thenReturn(ai);

        AnswerReviewDetailResponse detail = service.getAnswerForReview(answer.getAnswerPublicId(), host);

        assertThat(detail.ai()).isSameAs(ai);
    }

    @Test
    void detailOfAnotherTenantsAnswerIsNotFoundAndNeverReadsTheAiRow() {
        ScoringAnswer answer = answer(UUID.randomUUID());
        when(answerRepository.findByAnswerPublicId(answer.getAnswerPublicId())).thenReturn(Optional.of(answer));

        assertThatThrownBy(() -> service.getAnswerForReview(answer.getAnswerPublicId(), host))
                .isInstanceOf(AnswerNotFoundException.class);

        verifyNoInteractions(aiDetails);
    }

    @Test
    void listRowsFlagLowConfidenceAnswers() {
        ScoringAnswer low = answer(tenantId);
        ScoringAnswer normal = answer(tenantId);
        Page<ScoringAnswer> page = new PageImpl<>(List.of(low, normal), PageRequest.of(0, 20), 2);
        when(answerRepository.findForReview(tenantId, low.getSessionPublicId(), null, null, PageRequest.of(0, 20)))
                .thenReturn(page);
        when(aiDetails.findFlags(tenantId, List.of(low.getAnswerPublicId(), normal.getAnswerPublicId())))
                .thenReturn(Map.of(low.getAnswerPublicId(), new AiDetailFlags(low.getAnswerPublicId(), true, null)));

        var items = service.listAnswers(low.getSessionPublicId(), null, null, PageRequest.of(0, 20), host).items();

        assertThat(items.get(0).aiFallback()).isTrue();
        assertThat(items.get(1).aiFallback()).isFalse();
    }

    @Test
    void listCanBeNarrowedToOneAttempt() {
        ScoringAnswer mine = answer(tenantId);
        UUID attemptId = mine.getAttemptPublicId();
        when(answerRepository.findForReview(tenantId, mine.getSessionPublicId(), attemptId, null,
                PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(mine), PageRequest.of(0, 20), 1));
        when(aiDetails.findFlags(tenantId, List.of(mine.getAnswerPublicId()))).thenReturn(Map.of());

        var items = service.listAnswers(mine.getSessionPublicId(), attemptId, null, PageRequest.of(0, 20), host)
                .items();

        assertThat(items).hasSize(1);
        assertThat(items.get(0).attemptPublicId()).isEqualTo(attemptId);
    }

    @Test
    void listRowsSayWhichAnswersAreAiScored() {
        ScoringAnswer spoken = answer(tenantId);
        ScoringAnswer objective = answer(tenantId);
        Page<ScoringAnswer> page = new PageImpl<>(List.of(spoken, objective), PageRequest.of(0, 20), 2);
        when(answerRepository.findForReview(tenantId, spoken.getSessionPublicId(), null, null, PageRequest.of(0, 20)))
                .thenReturn(page);
        when(aiDetails.findFlags(tenantId, List.of(spoken.getAnswerPublicId(), objective.getAnswerPublicId())))
                .thenReturn(Map.of());
        when(eligibility.isAiEligible(spoken)).thenReturn(true);
        when(eligibility.isAiEligible(objective)).thenReturn(false);

        var items = service.listAnswers(spoken.getSessionPublicId(), null, null, PageRequest.of(0, 20), host).items();

        assertThat(items.get(0).aiScored()).isTrue();
        assertThat(items.get(1).aiScored()).isFalse();
    }

    private ScoringAnswer answer(UUID answerTenantId) {
        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(UUID.randomUUID());
        answer.setAttemptPublicId(UUID.randomUUID());
        answer.setSessionPublicId(UUID.randomUUID());
        answer.setTaskType("DESCRIBE_IMAGE");
        answer.setTenantId(answerTenantId);
        answer.setStatus(ScoringAnswerStatus.SCORED);
        answer.setRawScore(70);
        answer.setCreatedAt(Instant.now());
        return answer;
    }
}
