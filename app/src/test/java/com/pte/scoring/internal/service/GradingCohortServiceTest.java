package com.pte.scoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptGradingCandidateView;
import com.pte.attempt.dto.response.AttemptGradingCoverageView;
import com.pte.attempt.dto.response.AttemptGradingItemView;
import com.pte.scoring.SessionGradingCompletedEvent;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.GradingCohortStatus;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.dto.request.FinalizeGradingCohortRequest;
import com.pte.scoring.dto.request.GradingCohortDispositionRequest;
import com.pte.scoring.dto.response.GradingCohortAttemptResponse;
import com.pte.scoring.dto.response.GradingCohortPreviewResponse;
import com.pte.scoring.dto.response.GradingCohortResponse;
import com.pte.scoring.internal.constant.GradingConstants;
import com.pte.scoring.internal.exception.GradingCohortException;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.scoring.internal.repository.ExaminerEvaluationRepository;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.session.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradingCohortServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");

    @Mock private SessionService sessionService;
    @Mock private SessionGradingGateway gateway;
    @Mock private AttemptService attemptService;
    @Mock private ScoringIngestService ingestService;
    @Mock private AiEvaluationRepository answerRepository;
    @Mock private GradingAssignmentRepository assignmentRepository;
    @Mock private ExaminerEvaluationRepository examinerScoreRepository;
    @Mock private GradingCompletionEvaluator evaluator;
    @Mock private ScoreSelectionReader selectionReader;
    @Mock private ApplicationEventPublisher eventPublisher;

    private GradingCohortService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID submittedAttemptId = UUID.randomUUID();
    private final UUID submittedStudentId = UUID.randomUUID();
    private final UUID outstandingAttemptId = UUID.randomUUID();
    private final UUID outstandingStudentId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(sessionService.isSessionClosed(sessionId, tenantId)).thenReturn(true);
        service = new GradingCohortService(sessionService, gateway, attemptService, ingestService, answerRepository,
                assignmentRepository, examinerScoreRepository, evaluator, selectionReader, eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void finalizationRequiresDispositionForEveryOutstandingAttemptAndExcludesIt() {
        stubCandidates();
        GradingCohortPreviewResponse preview = service.preview(sessionId, tenantId);

        assertThatThrownBy(() -> service.finalizeCohort(sessionId, tenantId, actorId,
                new FinalizeGradingCohortRequest(preview.previewVersion(), GradingMarkingMode.AI_ONLY, List.of())))
                .isInstanceOf(GradingCohortException.class);
        verify(gateway, never()).freeze(any(), any(), any(), any(), any(), any());

        var request = new FinalizeGradingCohortRequest(preview.previewVersion(), GradingMarkingMode.AI_ONLY,
                List.of(new GradingCohortDispositionRequest(outstandingAttemptId, "Student withdrew before exam.")));
        when(gateway.freeze(tenantId, sessionId, GradingMarkingMode.AI_ONLY, preview.previewVersion(), actorId, NOW))
                .thenReturn(grading(GradingMarkingMode.AI_ONLY, GradingCohortStatus.FROZEN,
                        preview.previewVersion()));
        stubEvaluationInputs();
        when(evaluator.evaluate(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new GradingEvaluation(true, 1, 1, 1, 1, List.of()));
        when(gateway.complete(tenantId, sessionId, NOW)).thenReturn(true);

        GradingCohortResponse result = service.finalizeCohort(sessionId, tenantId, actorId, request);

        assertThat(result.complete()).isTrue();
        verify(attemptService).excludeFromGrading(sessionId, tenantId,
                Map.of(outstandingAttemptId, "Student withdrew before exam."));
        verify(eventPublisher).publishEvent(any(SessionGradingCompletedEvent.class));
    }

    @Test
    void finalizingAgainWithTheSameVersionAndModeIsIdempotent() {
        stubCandidates();
        String version = service.preview(sessionId, tenantId).previewVersion();
        when(gateway.grading(tenantId, sessionId)).thenReturn(Optional.of(
                grading(GradingMarkingMode.AI_ONLY, GradingCohortStatus.COMPLETED, version)));
        stubEvaluationInputs();
        when(evaluator.evaluate(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new GradingEvaluation(true, 1, 1, 1, 1, List.of()));

        GradingCohortResponse result = service.finalizeCohort(sessionId, tenantId, actorId,
                new FinalizeGradingCohortRequest(version, GradingMarkingMode.AI_ONLY, List.of()));

        assertThat(result.complete()).isTrue();
        verify(gateway, never()).freeze(any(), any(), any(), any(), any(), any());
        verify(attemptService, never()).excludeFromGrading(any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void finalizingAFrozenSessionInAnotherModeIsRejected() {
        stubCandidates();
        String version = service.preview(sessionId, tenantId).previewVersion();
        when(gateway.grading(tenantId, sessionId)).thenReturn(Optional.of(
                grading(GradingMarkingMode.MANUAL_EXAMINER, GradingCohortStatus.FROZEN, version)));

        assertThatThrownBy(() -> service.finalizeCohort(sessionId, tenantId, actorId,
                new FinalizeGradingCohortRequest(version, GradingMarkingMode.AI_ONLY, List.of())))
                .isInstanceOf(GradingCohortException.class);

        verify(gateway, never()).freeze(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aStalePreviewVersionIsRejectedBeforeFreezing() {
        stubCandidates();

        assertThatThrownBy(() -> service.finalizeCohort(sessionId, tenantId, actorId,
                new FinalizeGradingCohortRequest("stale", GradingMarkingMode.AI_ONLY, List.of())))
                .isInstanceOf(GradingCohortException.class);

        verify(gateway, never()).freeze(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aiOnlyIsRejectedOnceExaminersWereAssigned() {
        stubCandidates();
        String version = service.preview(sessionId, tenantId).previewVersion();
        when(assignmentRepository.countByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(2L);

        assertThatThrownBy(() -> service.finalizeCohort(sessionId, tenantId, actorId,
                new FinalizeGradingCohortRequest(version, GradingMarkingMode.AI_ONLY,
                        List.of(new GradingCohortDispositionRequest(outstandingAttemptId, "No show.")))))
                .isInstanceOf(GradingCohortException.class);

        verify(gateway, never()).freeze(any(), any(), any(), any(), any(), any());
    }

    @Test
    void reconcilingACompletedGradingNeitherAnnouncesCompletionAgainNorRewritesAnswers() {
        when(gateway.grading(tenantId, sessionId)).thenReturn(Optional.of(
                grading(GradingMarkingMode.AI_ONLY, GradingCohortStatus.COMPLETED, "preview-v1")));
        when(evaluator.evaluate(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new GradingEvaluation(true, 1, 0, 1, 1, List.of()));

        service.reconcileIfPresent(sessionId, tenantId);

        verify(eventPublisher, never()).publishEvent(any());
        // Reconcile runs inside the AI worker's commit, so it only inserts new answers and never
        // updates existing rows another worker may hold locked.
        verify(ingestService).ingestForSession(sessionId, tenantId);
    }

    @Test
    void reconcilingASessionWithoutGradingDoesNothing() {
        service.reconcileIfPresent(sessionId, tenantId);

        verifyNoInteractions(ingestService, eventPublisher);
    }

    @Test
    void previewRejectsAnOpenSessionBeforeReadingItsAttemptInventory() {
        when(sessionService.isSessionClosed(sessionId, tenantId)).thenReturn(false);

        assertThatThrownBy(() -> service.preview(sessionId, tenantId))
                .isInstanceOf(GradingCohortException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void previewOfAFrozenSessionReportsTheExcludedAttempts() {
        when(gateway.grading(tenantId, sessionId)).thenReturn(Optional.of(
                grading(GradingMarkingMode.MANUAL_EXAMINER, GradingCohortStatus.FROZEN, "preview-v1")));
        when(attemptService.getGradingCandidatesForSession(sessionId, tenantId)).thenReturn(List.of(
                new AttemptGradingCandidateView(submittedAttemptId, submittedStudentId, "SUBMITTED", "v1",
                        false, null),
                new AttemptGradingCandidateView(outstandingAttemptId, outstandingStudentId, "IN_PROGRESS", "v1",
                        true, "No show.")));

        GradingCohortPreviewResponse preview = service.preview(sessionId, tenantId);

        assertThat(preview.previewVersion()).isEqualTo("preview-v1");
        assertThat(preview.attempts()).extracting(GradingCohortAttemptResponse::excluded)
                .containsExactlyInAnyOrder(false, true);
    }

    @Test
    void ensuringAManualGradingFreezesItWithAnAutomaticReasonForUnsubmittedAttempts() {
        stubCandidates();
        when(gateway.freeze(eq(tenantId), eq(sessionId), eq(GradingMarkingMode.MANUAL_EXAMINER), any(),
                eq(actorId), eq(NOW)))
                .thenReturn(grading(GradingMarkingMode.MANUAL_EXAMINER, GradingCohortStatus.FROZEN, "v"));
        when(attemptService.getGradingCoverage(any(), any(), any())).thenReturn(Map.of());
        when(evaluator.evaluate(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new GradingEvaluation(false, 1, 1, 1, 0, List.of()));

        service.ensureManualCohort(sessionId, tenantId, actorId);

        verify(attemptService).excludeFromGrading(sessionId, tenantId,
                Map.of(outstandingAttemptId, GradingConstants.AUTO_DISPOSITION_NOT_SUBMITTED));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void ensuringAManualGradingLeavesAnExistingGradingUntouched() {
        when(gateway.grading(tenantId, sessionId)).thenReturn(Optional.of(
                grading(GradingMarkingMode.MANUAL_EXAMINER, GradingCohortStatus.FROZEN, "preview-v1")));

        service.ensureManualCohort(sessionId, tenantId, actorId);

        verify(gateway, never()).freeze(any(), any(), any(), any(), any(), any());
        verify(attemptService, never()).excludeFromGrading(any(), any(), any());
    }

    private SessionGradingGateway.Grading grading(GradingMarkingMode mode, GradingCohortStatus status,
            String previewVersion) {
        return new SessionGradingGateway.Grading(sessionId, tenantId, mode, status, previewVersion, actorId, NOW,
                status == GradingCohortStatus.COMPLETED ? NOW : null);
    }

    private void stubEvaluationInputs() {
        AiEvaluation answer = new AiEvaluation(tenantId, sessionId, submittedAttemptId, UUID.randomUUID(), itemId,
                templateId, "WRITE_ESSAY", "WRITING", ScoringMethod.AI_TEXT);
        answer.markAiScored(80, AiProviderCategory.REAL, "test", "model", "v1");
        when(answerRepository.findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(eq(sessionId), eq(tenantId),
                any())).thenReturn(List.of(answer));
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndAttemptPublicIdIn(eq(tenantId), eq(sessionId),
                any())).thenReturn(List.of());
        when(examinerScoreRepository.findSubmittedBySession(tenantId, sessionId)).thenReturn(List.of());
        when(attemptService.getGradingCoverage(any(), eq(sessionId), eq(tenantId)))
                .thenReturn(Map.of(submittedAttemptId, new AttemptGradingCoverageView(submittedAttemptId,
                        List.of(new AttemptGradingItemView(itemId, templateId, "WRITE_ESSAY")))));
    }

    private void stubCandidates() {
        when(attemptService.getGradingCandidatesForSession(sessionId, tenantId)).thenReturn(List.of(
                new AttemptGradingCandidateView(submittedAttemptId, submittedStudentId, "SUBMITTED", "submitted-v1",
                        false, null),
                new AttemptGradingCandidateView(outstandingAttemptId, outstandingStudentId, "IN_PROGRESS",
                        "outstanding-v1", false, null)));
    }
}
