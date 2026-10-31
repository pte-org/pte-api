package com.pte.scoring.internal.service.examiner;

import com.pte.scoring.internal.service.grading.SessionGradingGateway;
import com.pte.scoring.internal.service.staff.ScoringEligibilityQueryService;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.GradingAssignment;
import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus;
import com.pte.scoring.domain.enums.ExaminerDecisionKind;
import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptGradingCandidateView;
import com.pte.scoring.domain.enums.ExaminerGradingOperation;
import com.pte.scoring.domain.enums.GradingCohortStatus;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.dto.request.FinalizeExaminerWorkRequest;
import com.pte.scoring.dto.request.SaveExaminerDraftRequest;
import com.pte.scoring.dto.response.ExaminerCommandReceiptResponse;
import com.pte.scoring.dto.response.ExaminerCommandResponse;
import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.scoring.internal.exception.examiner.ExaminerReceiptConflictException;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.scoring.internal.repository.ExaminerEvaluationRepository;
import com.pte.scoring.internal.repository.GradingIssueRepository;
import com.pte.scoring.support.EvaluationFixtures;
import com.pte.session.SessionService;
import com.pte.shared.audit.AuditHistoryReader;
import com.pte.shared.audit.AuditLogChangeWriter;
import com.pte.shared.idempotency.IdempotencyDecision;
import com.pte.shared.idempotency.IdempotencyKeyService;
import com.pte.shared.idempotency.IdempotencyOutcome;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The examiner's draft and final score live on one row per answer: a draft is promoted to the submitted score in
 * place, a submitted score never takes a draft again, and each command is replayable by its request id.
 */
@ExtendWith(MockitoExtension.class)
class ExaminerDraftCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");

    private final UUID tenantId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID attemptId = UUID.randomUUID();
    private final UUID answerId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID examinerId = UUID.randomUUID();
    private final CurrentUser examiner = new CurrentUser(examinerId, tenantId, List.of("EXAMINER"));
    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

    @Mock
    private SessionService sessionService;
    @Mock
    private GradingAssignmentRepository assignmentRepository;
    @Mock
    private GradingIssueRepository issueRepository;
    @Mock
    private ExaminerEvaluationRepository evaluationRepository;
    @Mock
    private AiEvaluationRepository answerRepository;
    @Mock
    private SessionGradingGateway gateway;
    @Mock
    private AttemptService attemptService;
    @Mock
    private ScoringEligibilityQueryService eligibilityQueryService;
    @Mock
    private ExaminerWorkflowQueryService workflowQueryService;
    @Mock
    private IdempotencyKeyService idempotencyKeyService;
    @Mock
    private AuditLogChangeWriter auditWriter;
    @Mock
    private AuditHistoryReader auditReader;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private ExaminerDraftCommandService service;
    private GradingAssignment assignment;

    @BeforeEach
    void setUp() {
        ExaminerWorkRecorder recorder = new ExaminerWorkRecorder(idempotencyKeyService, auditWriter, auditReader,
                jsonMapper);
        service = new ExaminerDraftCommandService(sessionService, assignmentRepository,
                issueRepository, evaluationRepository, answerRepository, gateway, attemptService,
                eligibilityQueryService, workflowQueryService, new ExaminerReviewVersionService(),
                recorder, new ExaminerFinalizeSubmitter(evaluationRepository, new ExaminerReviewVersionService(),
                        attemptService), eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
        assignment = new GradingAssignment(tenantId, sessionId, attemptId, examinerId, 1,
                UUID.randomUUID(), NOW);
        ReflectionTestUtils.setField(assignment, "publicId", assignmentId);
        ReflectionTestUtils.setField(assignment, "id", 7L);
        assignment.start(NOW);
        lenient().when(assignmentRepository
                .findByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicIdAndDeletedFalse(tenantId,
                        sessionId, attemptId, examinerId)).thenReturn(Optional.of(assignment));
        lenient().when(assignmentRepository.findForUpdate(tenantId, assignmentId))
                .thenReturn(Optional.of(assignment));
        lenient().when(answerRepository
                .existsByTenantIdAndSessionPublicIdAndAttemptPublicIdAndAnswerPublicIdAndDeletedFalse(tenantId,
                        sessionId, attemptId, answerId)).thenReturn(true);
        lenient().when(evaluationRepository.save(any(ExaminerEvaluation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(idempotencyKeyService.begin(any(), any(), eq(tenantId), any(), any()))
                .thenReturn(new IdempotencyDecision(IdempotencyOutcome.NEW, null));
    }

    @Test
    void firstSaveCreatesTheDraftAtVersionOneAndStoresTheReceipt() {
        when(evaluationRepository.findByAnswerWithLock(tenantId, answerId)).thenReturn(Optional.empty());

        ExaminerCommandResponse response = service.saveDraft(sessionId, attemptId, answerId, draftRequest(0), examiner);

        assertThat(response.draft().draftVersion()).isEqualTo(1L);
        assertThat(response.draft().score()).isEqualTo(70);
        assertThat(response.receipt().operation()).isEqualTo(ExaminerGradingOperation.SAVE_DRAFT);
        verify(idempotencyKeyService).complete(any(), any(), eq(tenantId), any());
        verify(auditWriter).record(eq(examiner), any());
    }

    @Test
    void aSecondSaveBumpsTheDraftVersionOfTheSameRow() {
        ExaminerEvaluation draft = draft(70);
        when(evaluationRepository.findByAnswerWithLock(tenantId, answerId)).thenReturn(Optional.of(draft));

        ExaminerCommandResponse response = service.saveDraft(sessionId, attemptId, answerId, draftRequest(1), examiner);

        assertThat(response.draft().draftVersion()).isEqualTo(2L);
        assertThat(draft.getScore()).isEqualTo(70);
    }

    @Test
    void aStaleDraftVersionIsRejected() {
        when(evaluationRepository.findByAnswerWithLock(tenantId, answerId)).thenReturn(Optional.of(draft(70)));

        assertThatThrownBy(() -> service.saveDraft(sessionId, attemptId, answerId, draftRequest(5), examiner))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(auditWriter, never()).record(any(), any());
    }

    @Test
    void aDraftCannotBeSavedOnAnAnswerThatAlreadyHasASubmittedScore() {
        when(evaluationRepository.findByAnswerWithLock(tenantId, answerId)).thenReturn(Optional.of(submitted(80)));

        assertThatThrownBy(() -> service.saveDraft(sessionId, attemptId, answerId, draftRequest(1), examiner))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo(ExaminerScoringConstants.ANSWER_ALREADY_SUBMITTED);
                });
    }

    @Test
    void aRepeatedSaveReturnsTheStoredReceiptWithoutTouchingTheDraft() {
        ExaminerCommandReceiptResponse stored = new ExaminerCommandReceiptResponse(UUID.randomUUID(),
                ExaminerGradingOperation.SAVE_DRAFT, NOW, 1L, answerId, 1L, List.of());
        when(idempotencyKeyService.begin(any(), any(), eq(tenantId), any(), any())).thenReturn(
                new IdempotencyDecision(IdempotencyOutcome.REPLAY, jsonMapper.writeValueAsString(stored)));
        when(evaluationRepository.findByTenantIdAndAssignmentIdAndAnswerPublicIdAndDeletedFalse(tenantId, 7L, answerId))
                .thenReturn(Optional.of(draft(70)));

        ExaminerCommandResponse response = service.saveDraft(sessionId, attemptId, answerId, draftRequest(0), examiner);

        assertThat(response.receipt()).isEqualTo(stored);
        verify(evaluationRepository, never()).save(any());
        verify(auditWriter, never()).record(any(), any());
    }

    @Test
    void reusingTheRequestIdForADifferentSaveIsAConflict() {
        when(idempotencyKeyService.begin(any(), any(), eq(tenantId), any(), any()))
                .thenReturn(new IdempotencyDecision(IdempotencyOutcome.CONFLICT, null));

        assertThatThrownBy(() -> service.saveDraft(sessionId, attemptId, answerId, draftRequest(0), examiner))
                .isInstanceOf(ExaminerReceiptConflictException.class);

        verify(evaluationRepository, never()).save(any());
    }

    @Test
    void finalizePromotesTheDraftToTheSubmittedScoreInPlace() {
        stubFinalize();
        ExaminerEvaluation draft = draft(70);
        UUID evaluationId = UUID.randomUUID();
        ReflectionTestUtils.setField(draft, "publicId", evaluationId);
        when(evaluationRepository.findByTenantIdAndAssignmentIdAndDeletedFalse(tenantId, 7L)).thenReturn(List.of(draft));
        when(evaluationRepository.findSubmittedByAttempt(tenantId, sessionId, attemptId)).thenReturn(List.of());

        ExaminerCommandResponse response = service.finalizeWork(sessionId, attemptId,
                finalizeRequest(1L), examiner);

        assertThat(draft.getStatus()).isEqualTo(ExaminerAnswerScoreStatus.SUBMITTED);
        assertThat(draft.getScore()).isEqualTo(70);
        assertThat(draft.getSubmittedAt()).isEqualTo(NOW);
        assertThat(response.receipt().operation()).isEqualTo(ExaminerGradingOperation.FINALIZE);
        assertThat(response.receipt().childRequests()).hasSize(1);
        assertThat(assignment.getStatus()).isEqualTo(com.pte.scoring.domain.enums.ExaminerWorkflowStatus.COMPLETED);
        verify(eventPublisher).publishEvent(any(com.pte.scoring.ScoringProgressChangedEvent.class));
        verify(attemptService).selectScoreSource(java.util.Set.of(answerId), tenantId, "EXAMINER", examinerId, NOW,
                java.util.Map.of(answerId, evaluationId));
        verify(evaluationRepository).save(draft);
    }

    @Test
    void finalizeLeavesAnAlreadySubmittedScoreAsItIs() {
        stubFinalize();
        ExaminerEvaluation submitted = submitted(90);
        when(evaluationRepository.findByTenantIdAndAssignmentIdAndDeletedFalse(tenantId, 7L))
                .thenReturn(List.of(submitted));
        when(evaluationRepository.findSubmittedByAttempt(tenantId, sessionId, attemptId))
                .thenReturn(List.of(submitted));

        ExaminerCommandResponse response = service.finalizeWork(sessionId, attemptId,
                finalizeRequest(3L), examiner);

        assertThat(submitted.getScore()).isEqualTo(90);
        assertThat(response.receipt().childRequests()).isEmpty();
        verify(evaluationRepository, never()).save(any());
    }

    @Test
    void finalizeWithAStaleDraftVersionChangesNothing() {
        stubFinalize();
        ExaminerEvaluation draft = draft(70);
        when(evaluationRepository.findByTenantIdAndAssignmentIdAndDeletedFalse(tenantId, 7L)).thenReturn(List.of(draft));
        when(evaluationRepository.findSubmittedByAttempt(tenantId, sessionId, attemptId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.finalizeWork(sessionId, attemptId, finalizeRequest(9L), examiner))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(draft.getStatus()).isEqualTo(ExaminerAnswerScoreStatus.DRAFT);
        verify(evaluationRepository, never()).save(any());
    }

    @Test
    void aRepeatedFinalizeReturnsTheStoredReceipt() {
        ExaminerCommandReceiptResponse stored = new ExaminerCommandReceiptResponse(UUID.randomUUID(),
                ExaminerGradingOperation.FINALIZE, NOW, 1L, null, null, List.of());
        when(idempotencyKeyService.begin(any(), any(), eq(tenantId), any(), any())).thenReturn(
                new IdempotencyDecision(IdempotencyOutcome.REPLAY, jsonMapper.writeValueAsString(stored)));

        ExaminerCommandResponse response = service.finalizeWork(sessionId, attemptId, finalizeRequest(1L), examiner);

        assertThat(response.receipt()).isEqualTo(stored);
        verify(evaluationRepository, never()).save(any());
    }

    @Test
    void finalizeIsRefusedWhileACarriedOverDraftIsUnconfirmed() {
        stubFinalize();
        ExaminerEvaluation carried = carriedOverDraft(70);
        lenient().when(evaluationRepository.findByTenantIdAndAssignmentIdAndDeletedFalse(tenantId, 7L))
                .thenReturn(List.of(carried));
        lenient().when(evaluationRepository.findSubmittedByAttempt(tenantId, sessionId, attemptId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.finalizeWork(sessionId, attemptId, finalizeRequest(1L), examiner))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(carried.getStatus()).isEqualTo(ExaminerAnswerScoreStatus.DRAFT);
        verify(evaluationRepository, never()).save(any());
    }

    @Test
    void savingACarriedOverDraftConfirmsItEvenWithTheSameScore() {
        ExaminerEvaluation carried = carriedOverDraft(70);
        when(evaluationRepository.findByAnswerWithLock(tenantId, answerId)).thenReturn(Optional.of(carried));

        ExaminerCommandResponse response = service.saveDraft(sessionId, attemptId, answerId, draftRequest(1), examiner);

        assertThat(carried.isCarriedOver()).isFalse();
        assertThat(response.draft().carriedOver()).isFalse();
        assertThat(response.draft().score()).isEqualTo(70);
    }

    private ExaminerEvaluation carriedOverDraft(int score) {
        ExaminerEvaluation carried = ExaminerEvaluation.draft(tenantId, sessionId, attemptId, answerId,
                UUID.randomUUID(), 7L, score, "n", ExaminerDecisionKind.MANUAL, "rv", null, NOW);
        carried.carryOverTo(examinerId);
        assertThat(carried.isCarriedOver()).isTrue();
        return carried;
    }

    private void stubFinalize() {
        when(gateway.grading(tenantId, sessionId)).thenReturn(Optional.of(new SessionGradingGateway.Grading(
                sessionId, tenantId, GradingMarkingMode.MANUAL_EXAMINER, GradingCohortStatus.FROZEN, "preview-v1",
                UUID.randomUUID(), NOW, null)));
        when(attemptService.getGradingCandidate(attemptId, tenantId)).thenReturn(Optional.of(
                new AttemptGradingCandidateView(attemptId, UUID.randomUUID(), "SUBMITTED", "v1", false, null)));
        AiEvaluation ai = EvaluationFixtures.ai(tenantId, sessionId, attemptId, answerId, "READ_ALOUD",
                ScoringMethod.AI_SPEECH);
        ai.markAiScored(60, AiProviderCategory.REAL, "p", "m", "v", java.time.Instant.now());
        when(answerRepository.findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(sessionId, tenantId,
                List.of(attemptId))).thenReturn(List.of(ai));
        when(eligibilityQueryService.isAiEligible(ai)).thenReturn(true);
        when(issueRepository.findByTenantIdAndAssignmentIdAndDeletedFalseOrderByReportedAtAsc(tenantId, 7L))
                .thenReturn(List.of());
    }

    private SaveExaminerDraftRequest draftRequest(long expectedDraftVersion) {
        return new SaveExaminerDraftRequest(UUID.randomUUID(), assignment.getWorkVersion(), expectedDraftVersion, 70, "note",
                ExaminerDecisionKind.MANUAL, "rv", null);
    }

    private FinalizeExaminerWorkRequest finalizeRequest(long expectedDraftVersion) {
        return new FinalizeExaminerWorkRequest(UUID.randomUUID(), assignment.getWorkVersion(), List.of(
                new FinalizeExaminerWorkRequest.FinalizeAnswerItemRequest(answerId, expectedDraftVersion, "rv", null)));
    }

    private ExaminerEvaluation draft(int score) {
        return ExaminerEvaluation.draft(tenantId, sessionId, attemptId, answerId, examinerId, 7L, score, "n",
                ExaminerDecisionKind.MANUAL, "rv", null, NOW);
    }

    private ExaminerEvaluation submitted(int score) {
        return ExaminerEvaluation.submittedDirectly(tenantId, sessionId, attemptId, answerId, examinerId, score, NOW,
                null, null, null, null, null, null);
    }
}
