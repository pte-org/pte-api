package com.pte.scoring.internal.service.review;

import com.pte.scoring.internal.service.ai.AiScoreDetailQueryService;
import com.pte.scoring.internal.service.ai.AiScoringDispatcher;
import com.pte.scoring.internal.service.grading.SessionGradingGateway;
import com.pte.scoring.internal.service.selection.ScoreSelectionReader;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.ScoreSource;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.support.EvaluationFixtures;
import com.pte.scoring.internal.repository.AiDetailFlags;
import com.pte.scoring.internal.repository.ExaminerEvaluationRepository;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoringReviewReadQueryServiceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID attemptId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @Mock
    private AiEvaluationRepository answerRepository;
    @Mock
    private ExaminerEvaluationRepository examinerScoreRepository;
    @Mock
    private GradingAssignmentRepository assignmentRepository;
    @Mock
    private SessionGradingGateway gateway;
    @Mock
    private ScoreSelectionReader selectionReader;
    @Mock
    private AiScoreDetailQueryService aiDetails;
    @Mock
    private AiScoringDispatcher dispatcher;

    private ScoringReviewReadQueryService service;

    @BeforeEach
    void setUp() {
        lenient().when(selectionReader.forSession(sessionId, tenantId)).thenReturn(Map.of());
        service = new ScoringReviewReadQueryService(answerRepository, examinerScoreRepository,
                assignmentRepository, gateway, selectionReader, aiDetails, dispatcher);
    }

    @Test
    void reportInputs_keepZeroObjectiveScorePublishable() {
        AiEvaluation answer = answer("MC_READING_SINGLE");
        answer.markScored(0, java.time.Instant.now());
        when(answerRepository.findByAttemptPublicIdAndTenantId(attemptId, tenantId)).thenReturn(List.of(answer));
        when(examinerScoreRepository.findSubmittedBySession(tenantId, sessionId)).thenReturn(List.of());

        var input = service.findReportInputsForAttempt(tenantId, attemptId).getFirst();

        assertThat(input.selectedScore()).isZero();
        assertThat(input.publishable()).isTrue();
        assertThat(input.blockingReason()).isNull();
    }

    @Test
    void reportInputs_excludeUnscoredObjectiveWithoutBlockingPublication() {
        AiEvaluation answer = answer("MC_READING_SINGLE");
        when(answerRepository.findByAttemptPublicIdAndTenantId(attemptId, tenantId)).thenReturn(List.of(answer));
        when(examinerScoreRepository.findSubmittedBySession(tenantId, sessionId)).thenReturn(List.of());

        var input = service.findReportInputsForAttempt(tenantId, attemptId).getFirst();

        assertThat(input.selectedScore()).isNull();
        assertThat(input.publishable()).isTrue();
        assertThat(input.blockingReason()).isNull();
    }

    @Test
    void reportInputs_requireSelectedSourceForAiEligibleAnswer() {
        AiEvaluation answer = answer("READ_ALOUD");
        answer.markAiScored(0, AiProviderCategory.REAL, "provider", "model", "v1", java.time.Instant.now());
        when(answerRepository.findByAttemptPublicIdAndTenantId(attemptId, tenantId)).thenReturn(List.of(answer));
        when(examinerScoreRepository.findSubmittedBySession(tenantId, sessionId)).thenReturn(List.of());

        var input = service.findReportInputsForAttempt(tenantId, attemptId).getFirst();

        assertThat(input.selectedScore()).isNull();
        assertThat(input.publishable()).isFalse();
        assertThat(input.blockingReason()).isEqualTo("NO_SELECTED_SOURCE");
    }

    @Test
    void reportInputs_provisionalScoreFallsBackToTheAiScoreUntilASourceIsChosen() {
        AiEvaluation answer = answer("READ_ALOUD");
        answer.markAiScored(88, AiProviderCategory.REAL, "provider", "model", "v1", java.time.Instant.now());
        stubAiAnswer(answer);

        var input = service.findReportInputsForAttempt(tenantId, attemptId).getFirst();

        assertThat(input.provisionalScore()).isEqualTo(88);
        assertThat(input.selectedScore()).isNull();
        assertThat(input.publishable()).isFalse();
    }

    @Test
    void reportInputs_provisionalScoreIsEmptyWhenTheAnswerHasNoAiScore() {
        stubAiAnswer(answer("READ_ALOUD"));

        var input = service.findReportInputsForAttempt(tenantId, attemptId).getFirst();

        assertThat(input.provisionalScore()).isNull();
    }

    @Test
    void reportInputs_provisionalScoreEqualsTheSelectedScoreOnceChosen() {
        AiEvaluation answer = answer("READ_ALOUD");
        answer.markAiScored(88, AiProviderCategory.REAL, "provider", "model", "v1", java.time.Instant.now());
        selectAi(answer);
        stubAiAnswer(answer);

        var input = service.findReportInputsForAttempt(tenantId, attemptId).getFirst();

        assertThat(input.provisionalScore()).isEqualTo(input.selectedScore()).isEqualTo(88);
    }

    private void stubAiAnswer(AiEvaluation answer) {
        when(answerRepository.findByAttemptPublicIdAndTenantId(attemptId, tenantId)).thenReturn(List.of(answer));
        when(examinerScoreRepository.findSubmittedBySession(tenantId, sessionId)).thenReturn(List.of());
    }

    @Test
    void reportInputs_useOnlyTheSelectedRealAiScore() {
        AiEvaluation answer = answer("READ_ALOUD");
        answer.markAiScored(88, AiProviderCategory.REAL, "provider", "model", "v1", java.time.Instant.now());
        selectAi(answer);
        when(answerRepository.findByAttemptPublicIdAndTenantId(attemptId, tenantId)).thenReturn(List.of(answer));
        when(examinerScoreRepository.findSubmittedBySession(tenantId, sessionId)).thenReturn(List.of());

        var input = service.findReportInputsForAttempt(tenantId, attemptId).getFirst();

        assertThat(input.selectedScore()).isEqualTo(88);
        assertThat(input.aiRawScore()).isEqualTo(88);
        assertThat(input.aiProviderCategory()).isEqualTo("REAL");
        assertThat(input.publishable()).isTrue();
    }

    @Test
    void hostReview_countsFailedAndStuckAnswersAsRescorableButNotFreshOrPendingOnes() {
        Instant staleBefore = Instant.parse("2026-10-11T10:00:00Z");
        AiEvaluation failed = answer("READ_ALOUD");
        failed.markAiDispatched(staleBefore.minusSeconds(600));
        failed.setStatus(ScoringAnswerStatus.SCORING_FAILED);
        AiEvaluation stuck = answer("SPEAKING_STUCK");
        stuck.markAiDispatched(staleBefore.minusSeconds(60));
        AiEvaluation neverDispatched = answer("SPEAKING_LOST");
        neverDispatched.setStatus(ScoringAnswerStatus.AI_SCORING);
        AiEvaluation fresh = answer("SPEAKING_FRESH");
        fresh.markAiDispatched(staleBefore.plusSeconds(30));
        AiEvaluation pending = answer("SPEAKING_PENDING");
        stubHostReview(List.of(failed, stuck, neverDispatched, fresh, pending), staleBefore);

        var review = service.findHostReview(tenantId, sessionId);

        assertThat(review.rescorableAnswerCount()).isEqualTo(3);
    }

    @Test
    void hostReview_marksFallbackAndFailureReasonOnTheAnswerRows() {
        AiEvaluation failed = answer("DESCRIBE_IMAGE");
        failed.markAiDispatched(Instant.parse("2026-10-11T09:00:00Z"));
        failed.setStatus(ScoringAnswerStatus.SCORING_FAILED);
        AiEvaluation lowConfidence = answer("DESCRIBE_IMAGE");
        lowConfidence.markAiScored(61, AiProviderCategory.REAL, "gemini", "m", "v1", java.time.Instant.now());
        stubHostReview(List.of(failed, lowConfidence), Instant.parse("2026-10-11T10:00:00Z"));
        when(aiDetails.findFlags(tenantId, List.of(failed.getAnswerPublicId(), lowConfidence.getAnswerPublicId())))
                .thenReturn(java.util.Map.of(
                        failed.getAnswerPublicId(), new AiDetailFlags(failed.getAnswerPublicId(), false,
                                AiFailureReason.PROVIDER_TIMEOUT),
                        lowConfidence.getAnswerPublicId(), new AiDetailFlags(lowConfidence.getAnswerPublicId(),
                                true, null)));

        var review = service.findHostReview(tenantId, sessionId);

        var failedView = review.answers().stream()
                .filter(view -> view.answerPublicId().equals(failed.getAnswerPublicId())).findFirst().orElseThrow();
        var lowView = review.answers().stream()
                .filter(view -> view.answerPublicId().equals(lowConfidence.getAnswerPublicId())).findFirst()
                .orElseThrow();
        assertThat(failedView.aiFailureReason()).isEqualTo("PROVIDER_TIMEOUT");
        assertThat(failedView.aiFallback()).isFalse();
        assertThat(lowView.aiFallback()).isTrue();
        assertThat(lowView.aiFailureReason()).isNull();
    }

    @Test
    void hostReview_dropsTheOldFailureReasonOnceTheAnswerIsQueuedAgain() {
        AiEvaluation requeued = answer("DESCRIBE_IMAGE");
        requeued.markAiDispatched(Instant.parse("2026-10-11T10:00:00Z"));
        stubHostReview(List.of(requeued), Instant.parse("2026-10-11T09:00:00Z"));
        when(aiDetails.findFlags(tenantId, List.of(requeued.getAnswerPublicId())))
                .thenReturn(java.util.Map.of(requeued.getAnswerPublicId(), new AiDetailFlags(
                        requeued.getAnswerPublicId(), false, AiFailureReason.PROVIDER_TIMEOUT)));

        var review = service.findHostReview(tenantId, sessionId);

        assertThat(review.answers().getFirst().status()).isEqualTo("AI_SCORING");
        assertThat(review.answers().getFirst().aiFailureReason()).isNull();
    }

    private void stubHostReview(List<AiEvaluation> answers, Instant staleBefore) {
        when(answerRepository.findBySessionPublicIdAndTenantId(sessionId, tenantId)).thenReturn(answers);
        when(examinerScoreRepository.findSubmittedBySession(tenantId, sessionId)).thenReturn(List.of());
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of());
        when(dispatcher.staleBefore()).thenReturn(staleBefore);
    }

    /** The host chose the AI score of this answer. */
    private void selectAi(AiEvaluation answer) {
        when(selectionReader.forSession(sessionId, tenantId)).thenReturn(Map.of(answer.getAnswerPublicId(),
                new ScoreSelectionReader.Selection(ScoreSource.AI, Instant.now(), UUID.randomUUID(), null)));
    }

    private AiEvaluation answer(String taskType) {
        boolean objective = taskType.startsWith("MC_");
        return EvaluationFixtures.ai(tenantId, sessionId, attemptId, UUID.randomUUID(), taskType,
                objective ? ScoringMethod.OBJECTIVE : ScoringMethod.AI_SPEECH, objective ? "READING" : "SPEAKING");
    }
}
