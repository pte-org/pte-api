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
                eligibility, null, contentReader, com.pte.TestClocks.fixed());
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
        when(answerRepository.findForReview(tenantId, low.getSessionPublicId(), null, null, false, PageRequest.of(0, 20)))
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
        when(answerRepository.findForReview(tenantId, mine.getSessionPublicId(), attemptId, null, false,
                org.springframework.data.domain.Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(mine), PageRequest.of(0, 20), 1));
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
        when(answerRepository.findForReview(tenantId, spoken.getSessionPublicId(), null, null, false, PageRequest.of(0, 20)))
                .thenReturn(page);
        when(aiDetails.findFlags(tenantId, List.of(spoken.getAnswerPublicId(), objective.getAnswerPublicId())))
                .thenReturn(Map.of());
        when(eligibility.isAiEligible(spoken)).thenReturn(true);
        when(eligibility.isAiEligible(objective)).thenReturn(false);

        var items = service.listAnswers(spoken.getSessionPublicId(), null, null, PageRequest.of(0, 20), host).items();

        assertThat(items.get(0).aiScored()).isTrue();
        assertThat(items.get(1).aiScored()).isFalse();
    }

    @Test
    void listRowsFlagUnscoredAnswersAndPendingFilterDropsThem() {
        AiEvaluation unscored = EvaluationFixtures.ai(tenantId, UUID.randomUUID(), "PERSONAL_INTRODUCTION",
                ScoringMethod.UNSCORED);
        AiEvaluation spoken = EvaluationFixtures.ai(tenantId, unscored.getSessionPublicId(), "READ_ALOUD",
                ScoringMethod.AI_SPEECH);
        when(answerRepository.findForReview(tenantId, unscored.getSessionPublicId(), null, null, false,
                PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(unscored, spoken), PageRequest.of(0, 20), 2));
        when(answerRepository.findForReview(tenantId, unscored.getSessionPublicId(), null,
                com.pte.scoring.domain.enums.ScoringAnswerStatus.PENDING, true, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(spoken), PageRequest.of(0, 20), 1));
        when(aiDetails.findFlags(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Map.of());

        var all = service.listAnswers(unscored.getSessionPublicId(), null, null, PageRequest.of(0, 20), host).items();
        var pending = service.listAnswers(unscored.getSessionPublicId(), null, "PENDING", PageRequest.of(0, 20), host)
                .items();

        assertThat(all.get(0).unscored()).isTrue();
        assertThat(all.get(1).unscored()).isFalse();
        assertThat(pending).extracting("unscored").containsExactly(false);
    }

    @Test
    void listOfOneAttemptReportsHowManyExpectedItemsWereLeftBlank() {
        com.pte.attempt.AttemptService attempts = org.mockito.Mockito.mock(com.pte.attempt.AttemptService.class);
        service = new ScoringReviewService(answerRepository, decoder, mediaService, null, null, attempts, aiDetails,
                eligibility, null, contentReader, com.pte.TestClocks.fixed());
        AiEvaluation mine = answer(tenantId);
        UUID sessionId = mine.getSessionPublicId();
        UUID attemptId = mine.getAttemptPublicId();
        var expected = List.of(
                new com.pte.attempt.dto.response.AttemptGradingItemView(mine.getItemPublicId(), UUID.randomUUID(), "DESCRIBE_IMAGE", 2),
                new com.pte.attempt.dto.response.AttemptGradingItemView(UUID.randomUUID(), UUID.randomUUID(), "READ_ALOUD"),
                new com.pte.attempt.dto.response.AttemptGradingItemView(UUID.randomUUID(), UUID.randomUUID(), "RETELL_LECTURE"));
        when(answerRepository.findForReview(tenantId, sessionId, attemptId, null, false,
                org.springframework.data.domain.Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(mine), PageRequest.of(0, 20), 1));
        when(aiDetails.findFlags(tenantId, List.of(mine.getAnswerPublicId()))).thenReturn(Map.of());
        when(attempts.getGradingCoverage(List.of(attemptId), sessionId, tenantId)).thenReturn(
                Map.of(attemptId, new com.pte.attempt.dto.response.AttemptGradingCoverageView(attemptId, expected)));
        when(answerRepository.findByTenantIdAndSessionPublicIdAndAttemptPublicIdAndDeletedFalse(tenantId, sessionId,
                attemptId)).thenReturn(List.of(mine));

        var response = service.listAnswers(sessionId, attemptId, null, PageRequest.of(0, 20), host);

        assertThat(response.expectedCount()).isEqualTo(3);
        assertThat(response.answeredCount()).isEqualTo(1);
        assertThat(response.items()).singleElement().extracting(item -> item.questionNumber()).isEqualTo(3);
    }

    @Test
    void listOfOneAttemptIsOrderedByQuestionNumberAcrossPages() {
        com.pte.attempt.AttemptService attempts = org.mockito.Mockito.mock(com.pte.attempt.AttemptService.class);
        service = new ScoringReviewService(answerRepository, decoder, mediaService, null, null, attempts, aiDetails,
                eligibility, null, contentReader, com.pte.TestClocks.fixed());
        AiEvaluation third = answer(tenantId);
        AiEvaluation first = EvaluationFixtures.ai(tenantId, third.getSessionPublicId(), "READ_ALOUD",
                ScoringMethod.AI_SPEECH);
        UUID sessionId = third.getSessionPublicId();
        UUID attemptId = third.getAttemptPublicId();
        var expected = List.of(
                new com.pte.attempt.dto.response.AttemptGradingItemView(first.getItemPublicId(), UUID.randomUUID(), "READ_ALOUD", 0),
                new com.pte.attempt.dto.response.AttemptGradingItemView(third.getItemPublicId(), UUID.randomUUID(), "DESCRIBE_IMAGE", 2));
        // Repository order is newest first; the host still sees question 1 before question 3, on page one.
        when(answerRepository.findForReview(tenantId, sessionId, attemptId, null, false,
                org.springframework.data.domain.Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(third, first)));
        when(aiDetails.findFlags(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Map.of());
        when(attempts.getGradingCoverage(List.of(attemptId), sessionId, tenantId)).thenReturn(
                Map.of(attemptId, new com.pte.attempt.dto.response.AttemptGradingCoverageView(attemptId, expected)));
        when(answerRepository.findByTenantIdAndSessionPublicIdAndAttemptPublicIdAndDeletedFalse(tenantId, sessionId,
                attemptId)).thenReturn(List.of(third, first));

        var firstPage = service.listAnswers(sessionId, attemptId, null, PageRequest.of(0, 1), host);
        var secondPage = service.listAnswers(sessionId, attemptId, null, PageRequest.of(1, 1), host);

        assertThat(firstPage.items()).extracting(item -> item.questionNumber()).containsExactly(1);
        assertThat(secondPage.items()).extracting(item -> item.questionNumber()).containsExactly(3);
        assertThat(firstPage.totalElements()).isEqualTo(2);
    }

    @Test
    void listAcrossTheWholeSessionHasNoCoverageCounts() {
        AiEvaluation mine = answer(tenantId);
        when(answerRepository.findForReview(tenantId, mine.getSessionPublicId(), null, null, false,
                PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(mine), PageRequest.of(0, 20), 1));
        when(aiDetails.findFlags(tenantId, List.of(mine.getAnswerPublicId()))).thenReturn(Map.of());

        var response = service.listAnswers(mine.getSessionPublicId(), null, null, PageRequest.of(0, 20), host);

        assertThat(response.expectedCount()).isNull();
        assertThat(response.answeredCount()).isNull();
        assertThat(response.items()).singleElement().extracting(item -> item.questionNumber()).isNull();
    }

    private AiEvaluation answer(UUID answerTenantId) {
        AiEvaluation answer = EvaluationFixtures.ai(answerTenantId, UUID.randomUUID(), "DESCRIBE_IMAGE",
                ScoringMethod.AI_SPEECH);
        answer.markScored(70, java.time.Instant.now());
        return answer;
    }
}
