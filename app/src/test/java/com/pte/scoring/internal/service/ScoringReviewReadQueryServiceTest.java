package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.ScoreSource;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.internal.repository.AiDetailFlags;
import com.pte.scoring.internal.repository.ExaminerAnswerScoreRepository;
import com.pte.scoring.internal.repository.ExaminerAttemptAssignmentRepository;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.scoring.internal.repository.ScoringSessionStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoringReviewReadQueryServiceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID attemptId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();

    @Mock
    private ScoringAnswerRepository answerRepository;
    @Mock
    private ExaminerAnswerScoreRepository examinerScoreRepository;
    @Mock
    private ExaminerAttemptAssignmentRepository assignmentRepository;
    @Mock
    private ScoringSessionStateRepository sessionStateRepository;
    @Mock
    private ScoringMethodResolver methodResolver;
    @Mock
    private AiScoreDetailQueryService aiDetails;
    @Mock
    private AiScoringDispatcher dispatcher;

    private ScoringReviewReadQueryService service;

    @BeforeEach
    void setUp() {
        service = new ScoringReviewReadQueryService(answerRepository, examinerScoreRepository,
                assignmentRepository, sessionStateRepository, methodResolver, aiDetails, dispatcher);
    }

    @Test
    void reportInputs_keepZeroObjectiveScorePublishable() {
        ScoringAnswer answer = answer("MC_READING_SINGLE");
        answer.markScored(0);
        when(answerRepository.findByAttemptPublicIdAndTenantId(attemptId, tenantId)).thenReturn(List.of(answer));
        when(examinerScoreRepository.findByTenantIdAndSessionPublicId(tenantId, sessionId)).thenReturn(List.of());
        when(methodResolver.resolve(templateId, "MC_READING_SINGLE"))
                .thenReturn(Optional.of(ScoringMethod.OBJECTIVE));
        when(methodResolver.resolveSection(templateId, "MC_READING_SINGLE")).thenReturn(Optional.of("READING"));

        var input = service.findReportInputsForAttempt(tenantId, attemptId).getFirst();

        assertThat(input.selectedScore()).isZero();
        assertThat(input.publishable()).isTrue();
        assertThat(input.blockingReason()).isNull();
    }

    @Test
    void reportInputs_excludeUnscoredObjectiveWithoutBlockingPublication() {
        ScoringAnswer answer = answer("MC_READING_SINGLE");
        when(answerRepository.findByAttemptPublicIdAndTenantId(attemptId, tenantId)).thenReturn(List.of(answer));
        when(examinerScoreRepository.findByTenantIdAndSessionPublicId(tenantId, sessionId)).thenReturn(List.of());
        when(methodResolver.resolve(templateId, "MC_READING_SINGLE"))
                .thenReturn(Optional.of(ScoringMethod.OBJECTIVE));
        when(methodResolver.resolveSection(templateId, "MC_READING_SINGLE")).thenReturn(Optional.of("READING"));

        var input = service.findReportInputsForAttempt(tenantId, attemptId).getFirst();

        assertThat(input.selectedScore()).isNull();
        assertThat(input.publishable()).isTrue();
        assertThat(input.blockingReason()).isNull();
    }

    @Test
    void reportInputs_requireSelectedSourceForAiEligibleAnswer() {
        ScoringAnswer answer = answer("READ_ALOUD");
        answer.markAiScored(0, AiProviderCategory.REAL, "provider", "model", "v1");
        when(answerRepository.findByAttemptPublicIdAndTenantId(attemptId, tenantId)).thenReturn(List.of(answer));
        when(examinerScoreRepository.findByTenantIdAndSessionPublicId(tenantId, sessionId)).thenReturn(List.of());
        when(methodResolver.resolve(templateId, "READ_ALOUD")).thenReturn(Optional.of(ScoringMethod.AI_SPEECH));
        when(methodResolver.resolveSection(templateId, "READ_ALOUD")).thenReturn(Optional.of("SPEAKING"));

        var input = service.findReportInputsForAttempt(tenantId, attemptId).getFirst();

        assertThat(input.selectedScore()).isNull();
        assertThat(input.publishable()).isFalse();
        assertThat(input.blockingReason()).isEqualTo("NO_SELECTED_SOURCE");
    }

    @Test
    void reportInputs_provisionalScoreFallsBackToTheAiScoreUntilASourceIsChosen() {
        ScoringAnswer answer = answer("READ_ALOUD");
        answer.markAiScored(88, AiProviderCategory.REAL, "provider", "model", "v1");
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
        ScoringAnswer answer = answer("READ_ALOUD");
        answer.markAiScored(88, AiProviderCategory.REAL, "provider", "model", "v1");
        answer.selectScoreSource(ScoreSource.AI, UUID.randomUUID(), Instant.now(), null);
        stubAiAnswer(answer);

        var input = service.findReportInputsForAttempt(tenantId, attemptId).getFirst();

        assertThat(input.provisionalScore()).isEqualTo(input.selectedScore()).isEqualTo(88);
    }

    private void stubAiAnswer(ScoringAnswer answer) {
        when(answerRepository.findByAttemptPublicIdAndTenantId(attemptId, tenantId)).thenReturn(List.of(answer));
        when(examinerScoreRepository.findByTenantIdAndSessionPublicId(tenantId, sessionId)).thenReturn(List.of());
        when(methodResolver.resolve(templateId, "READ_ALOUD")).thenReturn(Optional.of(ScoringMethod.AI_SPEECH));
        when(methodResolver.resolveSection(templateId, "READ_ALOUD")).thenReturn(Optional.of("SPEAKING"));
    }

    @Test
    void reportInputs_useOnlyTheSelectedRealAiScore() {
        ScoringAnswer answer = answer("READ_ALOUD");
        answer.markAiScored(88, AiProviderCategory.REAL, "provider", "model", "v1");
        answer.selectScoreSource(ScoreSource.AI, UUID.randomUUID(), Instant.now(), null);
        when(answerRepository.findByAttemptPublicIdAndTenantId(attemptId, tenantId)).thenReturn(List.of(answer));
        when(examinerScoreRepository.findByTenantIdAndSessionPublicId(tenantId, sessionId)).thenReturn(List.of());
        when(methodResolver.resolve(templateId, "READ_ALOUD")).thenReturn(Optional.of(ScoringMethod.AI_SPEECH));
        when(methodResolver.resolveSection(templateId, "READ_ALOUD")).thenReturn(Optional.of("SPEAKING"));

        var input = service.findReportInputsForAttempt(tenantId, attemptId).getFirst();

        assertThat(input.selectedScore()).isEqualTo(88);
        assertThat(input.aiRawScore()).isEqualTo(88);
        assertThat(input.aiProviderCategory()).isEqualTo("REAL");
        assertThat(input.publishable()).isTrue();
    }

    @Test
    void hostReview_countsFailedAndStuckAnswersAsRescorableButNotFreshOrPendingOnes() {
        Instant staleBefore = Instant.parse("2026-10-11T10:00:00Z");
        ScoringAnswer failed = answer("READ_ALOUD");
        failed.markAiDispatched(staleBefore.minusSeconds(600));
        failed.setStatus(ScoringAnswerStatus.SCORING_FAILED);
        ScoringAnswer stuck = answer("SPEAKING_STUCK");
        stuck.markAiDispatched(staleBefore.minusSeconds(60));
        ScoringAnswer neverDispatched = answer("SPEAKING_LOST");
        neverDispatched.setStatus(ScoringAnswerStatus.AI_SCORING);
        ScoringAnswer fresh = answer("SPEAKING_FRESH");
        fresh.markAiDispatched(staleBefore.plusSeconds(30));
        ScoringAnswer pending = answer("SPEAKING_PENDING");
        stubHostReview(List.of(failed, stuck, neverDispatched, fresh, pending), staleBefore);

        var review = service.findHostReview(tenantId, sessionId);

        assertThat(review.rescorableAnswerCount()).isEqualTo(3);
    }

    @Test
    void hostReview_marksFallbackAndFailureReasonOnTheAnswerRows() {
        ScoringAnswer failed = answer("DESCRIBE_IMAGE");
        failed.markAiDispatched(Instant.parse("2026-10-11T09:00:00Z"));
        failed.setStatus(ScoringAnswerStatus.SCORING_FAILED);
        ScoringAnswer lowConfidence = answer("DESCRIBE_IMAGE");
        lowConfidence.markAiScored(61, AiProviderCategory.REAL, "gemini", "m", "v1");
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
        ScoringAnswer requeued = answer("DESCRIBE_IMAGE");
        requeued.markAiDispatched(Instant.parse("2026-10-11T10:00:00Z"));
        stubHostReview(List.of(requeued), Instant.parse("2026-10-11T09:00:00Z"));
        when(aiDetails.findFlags(tenantId, List.of(requeued.getAnswerPublicId())))
                .thenReturn(java.util.Map.of(requeued.getAnswerPublicId(), new AiDetailFlags(
                        requeued.getAnswerPublicId(), false, AiFailureReason.PROVIDER_TIMEOUT)));

        var review = service.findHostReview(tenantId, sessionId);

        assertThat(review.answers().getFirst().status()).isEqualTo("AI_SCORING");
        assertThat(review.answers().getFirst().aiFailureReason()).isNull();
    }

    private void stubHostReview(List<ScoringAnswer> answers, Instant staleBefore) {
        when(answerRepository.findBySessionPublicIdAndTenantId(sessionId, tenantId)).thenReturn(answers);
        when(examinerScoreRepository.findByTenantIdAndSessionPublicId(tenantId, sessionId)).thenReturn(List.of());
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of());
        when(sessionStateRepository.findByTenantIdAndSessionPublicId(tenantId, sessionId))
                .thenReturn(Optional.empty());
        when(methodResolver.resolve(org.mockito.ArgumentMatchers.eq(templateId),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.of(ScoringMethod.AI_SPEECH));
        when(methodResolver.resolveSection(org.mockito.ArgumentMatchers.eq(templateId),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.of("SPEAKING"));
        when(dispatcher.staleBefore()).thenReturn(staleBefore);
    }

    private ScoringAnswer answer(String taskType) {
        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(UUID.randomUUID());
        answer.setAttemptPublicId(attemptId);
        answer.setPinnedItemPublicId(UUID.randomUUID());
        answer.setSessionPublicId(sessionId);
        answer.setTenantId(tenantId);
        answer.setScoreTemplatePublicId(templateId);
        answer.setTaskType(taskType);
        return answer;
    }
}
