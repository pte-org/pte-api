package com.pte.scoring.internal.service;

import com.pte.scoring.internal.service.ai.AiScoreDetailQueryService;
import com.pte.scoring.internal.service.objective.AnswerContentReader;
import com.pte.scoring.internal.service.objective.AnswerPayloadDecoder;
import com.pte.scoring.internal.service.review.ScoringReviewService;
import com.pte.scoring.internal.service.staff.ScoringEligibilityQueryService;
import com.pte.media.MediaService;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.support.EvaluationFixtures;
import com.pte.scoring.support.SubmittedAnswerFixtures;
import com.pte.scoring.dto.response.AiScoreDetailResponse;
import com.pte.scoring.internal.dto.response.AnswerPayloadKind;
import com.pte.scoring.internal.dto.response.AnswerReviewDetailResponse;
import com.pte.scoring.internal.dto.response.DecodedAnswerPayload;
import com.pte.scoring.internal.exception.AnswerNotFoundException;
import com.pte.scoring.internal.repository.AiDetailFlags;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
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
    private AiEvaluationRepository answerRepository;
    @Mock
    private AnswerPayloadDecoder decoder;
    @Mock
    private MediaService mediaService;
    @Mock
    private AiScoreDetailQueryService aiDetails;
    @Mock
    private ScoringEligibilityQueryService eligibility;
    @Mock
    private AnswerContentReader contentReader;

    private ScoringReviewService service;
    private CurrentUser host;

    @BeforeEach
    void setUp() {
        service = new ScoringReviewService(answerRepository, decoder, mediaService, null, null, null, aiDetails,
                eligibility, null, contentReader, java.time.Clock.systemUTC());
        host = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
    }

    @Test
    void detailIncludesTheAiScoreAndItsBreakdown() {
        AiEvaluation answer = answer(tenantId);
        AiScoreDetailResponse ai = new AiScoreDetailResponse("SCORED", 72, Map.of("CONTENT", 80), "Good.",
                "spoken words", Map.of(), false, null);
        when(answerRepository.findByAnswerPublicId(answer.getAnswerPublicId())).thenReturn(Optional.of(answer));
        SubmittedAnswerView content = SubmittedAnswerFixtures.answer("DESCRIBE_IMAGE", null, "x");
        when(contentReader.one(answer.getAnswerPublicId(), tenantId)).thenReturn(Optional.of(content));
        when(decoder.decode(content)).thenReturn(new DecodedAnswerPayload(
                AnswerPayloadKind.TEXT, "x", null, null, null, null, null));
        when(aiDetails.find(answer)).thenReturn(ai);

        AnswerReviewDetailResponse detail = service.getAnswerForReview(answer.getAnswerPublicId(), host);

        assertThat(detail.ai()).isSameAs(ai);
    }

    @Test
    void detailOfAnotherTenantsAnswerIsNotFoundAndNeverReadsTheAiRow() {
        AiEvaluation answer = answer(UUID.randomUUID());
        when(answerRepository.findByAnswerPublicId(answer.getAnswerPublicId())).thenReturn(Optional.of(answer));

        assertThatThrownBy(() -> service.getAnswerForReview(answer.getAnswerPublicId(), host))
                .isInstanceOf(AnswerNotFoundException.class);

        verifyNoInteractions(aiDetails);
    }

    @Test
    void listRowsFlagLowConfidenceAnswers() {
        AiEvaluation low = answer(tenantId);
        AiEvaluation normal = answer(tenantId);
        Page<AiEvaluation> page = new PageImpl<>(List.of(low, normal), PageRequest.of(0, 20), 2);
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
        AiEvaluation mine = answer(tenantId);
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
        AiEvaluation spoken = answer(tenantId);
        AiEvaluation objective = answer(tenantId);
        Page<AiEvaluation> page = new PageImpl<>(List.of(spoken, objective), PageRequest.of(0, 20), 2);
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

    private AiEvaluation answer(UUID answerTenantId) {
        AiEvaluation answer = EvaluationFixtures.ai(answerTenantId, UUID.randomUUID(), "DESCRIBE_IMAGE",
                ScoringMethod.AI_SPEECH);
        answer.markScored(70, java.time.Instant.now());
        return answer;
    }
}
