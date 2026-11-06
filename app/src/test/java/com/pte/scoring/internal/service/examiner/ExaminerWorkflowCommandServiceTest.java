package com.pte.scoring.internal.service.examiner;

import com.pte.scoring.domain.GradingAssignment;
import com.pte.scoring.domain.GradingIssue;
import com.pte.scoring.domain.enums.ExaminerGradingIssueType;
import com.pte.scoring.domain.enums.ExaminerGradingOperation;
import com.pte.scoring.domain.enums.ExaminerWorkflowStatus;
import com.pte.scoring.dto.request.ReportGradingIssueRequest;
import com.pte.scoring.dto.request.ResolveGradingIssueRequest;
import com.pte.scoring.dto.request.StartExaminerWorkRequest;
import com.pte.scoring.dto.response.ExaminerCommandReceiptResponse;
import com.pte.scoring.dto.response.ExaminerCommandResponse;
import com.pte.scoring.dto.response.ExaminerGradingIssueResponse;
import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.scoring.internal.repository.GradingIssueRepository;
import com.pte.scoring.internal.service.grading.SessionGradingGateway;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Start, reopen, issue reporting and resolution of examiner grading work. */
@ExtendWith(MockitoExtension.class)
class ExaminerWorkflowCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T09:00:00Z");

    @Mock
    private SessionService sessionService;
    @Mock
    private GradingAssignmentRepository assignmentRepository;
    @Mock
    private GradingIssueRepository issueRepository;
    @Mock
    private SessionGradingGateway gateway;
    @Mock
    private ExaminerWorkflowQueryService workflowQueryService;
    @Mock
    private ExaminerWorkRecorder recorder;

    private ExaminerWorkflowCommandService service;
    private UUID tenantId;
    private UUID sessionId;
    private UUID attemptId;
    private CurrentUser examiner;
    private GradingAssignment assignment;

    @BeforeEach
    void setUp() {
        service = new ExaminerWorkflowCommandService(sessionService, assignmentRepository, issueRepository, gateway,
                workflowQueryService, recorder, Clock.fixed(NOW, ZoneOffset.UTC));
        tenantId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        attemptId = UUID.randomUUID();
        examiner = new CurrentUser(UUID.randomUUID(), tenantId, List.of("EXAMINER"));
        assignment = new GradingAssignment(tenantId, sessionId, attemptId, examiner.userId(), 3,
                UUID.randomUUID(), NOW.minusSeconds(600));
        assignment.setPublicId(UUID.randomUUID());
    }

    private void assignmentFound() {
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicIdAndDeletedFalse(
                tenantId, sessionId, attemptId, examiner.userId())).thenReturn(Optional.of(assignment));
    }

    private void lockReturnsAssignment() {
        when(assignmentRepository.findForUpdate(tenantId, assignment.getPublicId())).thenReturn(Optional.of(assignment));
    }

    private void saveEchoesTheAssignment() {
        when(assignmentRepository.save(any(GradingAssignment.class))).thenAnswer(call -> call.getArgument(0));
    }

    private void noReplay() {
        when(recorder.replayOrBegin(eq(examiner), eq(assignment.getPublicId()), any(UUID.class), anyString(), eq(NOW)))
                .thenReturn(Optional.empty());
    }

    private StartExaminerWorkRequest startRequest(long expectedVersion) {
        return new StartExaminerWorkRequest(UUID.randomUUID(), expectedVersion);
    }

    @Test
    void start_assignmentOfAnotherExaminer_isNotFound() {
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicIdAndDeletedFalse(
                tenantId, sessionId, attemptId, examiner.userId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(sessionId, attemptId, startRequest(0), examiner))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void start_scoresAlreadyPublished_isConflict() {
        assignmentFound();
        when(gateway.isPublished(tenantId, sessionId)).thenReturn(true);

        assertThatThrownBy(() -> service.start(sessionId, attemptId, startRequest(0), examiner))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo(ExaminerScoringConstants.SESSION_SCORES_PUBLISHED);
                });
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void start_unstartedWorkWithExpectedVersionAboveZero_isConflict() {
        assignmentFound();
        noReplay();
        lockReturnsAssignment();

        assertThatThrownBy(() -> service.start(sessionId, attemptId, startRequest(2), examiner))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(assignment.isStarted()).isFalse();
    }

    @Test
    void start_firstStart_movesWorkToInProgressAtVersionOneAndRecordsStart() {
        assignmentFound();
        noReplay();
        lockReturnsAssignment();
        saveEchoesTheAssignment();

        ExaminerCommandResponse response = service.start(sessionId, attemptId, startRequest(0), examiner);

        assertThat(assignment.getStatus()).isEqualTo(ExaminerWorkflowStatus.IN_PROGRESS);
        assertThat(assignment.getWorkVersion()).isEqualTo(1L);
        assertThat(response.receipt().operation()).isEqualTo(ExaminerGradingOperation.START);
        assertThat(response.receipt().workVersion()).isEqualTo(1L);
        assertThat(response.receipt().committedAt()).isEqualTo(NOW);
        verify(recorder).complete(eq(examiner), eq(assignment.getPublicId()), any(UUID.class),
                any(ExaminerCommandReceiptResponse.class));
    }

    @Test
    void start_startedWorkWithStaleVersion_isConflictWithBothVersionsInTheMessage() {
        assignment.start(NOW.minusSeconds(60));
        assignmentFound();
        noReplay();
        lockReturnsAssignment();

        assertThatThrownBy(() -> service.start(sessionId, attemptId, startRequest(5), examiner))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo(
                            ExaminerScoringConstants.WORK_VERSION_FORMAT.formatted(5L, 1L));
                });
    }

    @Test
    void start_finalizedWork_isReopenedAsRegrade() {
        assignment.start(NOW.minusSeconds(120));
        assignment.finalizeGrading(NOW.minusSeconds(60));
        assignmentFound();
        noReplay();
        lockReturnsAssignment();
        saveEchoesTheAssignment();

        ExaminerCommandResponse response = service.start(sessionId, attemptId, startRequest(2), examiner);

        assertThat(assignment.getStatus()).isEqualTo(ExaminerWorkflowStatus.IN_PROGRESS);
        assertThat(assignment.getWorkVersion()).isEqualTo(3L);
        assertThat(response.receipt().operation()).isEqualTo(ExaminerGradingOperation.REGRADE);
    }

    @Test
    void start_repeatedRequest_returnsStoredReceiptWithoutChangingTheWork() {
        assignmentFound();
        ExaminerCommandReceiptResponse stored = new ExaminerCommandReceiptResponse(UUID.randomUUID(),
                ExaminerGradingOperation.START, NOW.minusSeconds(5), 1L, null, null, Collections.emptyList());
        when(recorder.replayOrBegin(eq(examiner), eq(assignment.getPublicId()), any(UUID.class), anyString(), eq(NOW)))
                .thenReturn(Optional.of(stored));

        ExaminerCommandResponse response = service.start(sessionId, attemptId, startRequest(0), examiner);

        assertThat(response.receipt()).isSameAs(stored);
        assertThat(assignment.isStarted()).isFalse();
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void getCommand_unknownReceipt_isNotFound() {
        assignmentFound();
        UUID requestId = UUID.randomUUID();
        when(recorder.findReceipt(examiner, assignment.getPublicId(), requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCommand(sessionId, attemptId, requestId, examiner))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo(ExaminerScoringConstants.COMMAND_RECEIPT_NOT_FOUND);
                });
    }

    @Test
    void getCommand_knownReceipt_returnsItWithTheCurrentView() {
        assignmentFound();
        UUID requestId = UUID.randomUUID();
        ExaminerCommandReceiptResponse stored = new ExaminerCommandReceiptResponse(requestId,
                ExaminerGradingOperation.START, NOW, 1L, null, null, Collections.emptyList());
        when(recorder.findReceipt(examiner, assignment.getPublicId(), requestId)).thenReturn(Optional.of(stored));

        assertThat(service.getCommand(sessionId, attemptId, requestId, examiner).receipt()).isSameAs(stored);
    }

    private ReportGradingIssueRequest issueRequest(long expectedVersion) {
        return new ReportGradingIssueRequest(UUID.randomUUID(), expectedVersion, UUID.randomUUID(),
                ExaminerGradingIssueType.AUDIO_UNAVAILABLE, "  No sound  ");
    }

    @Test
    void reportIssue_beforeWorkStarted_isBadRequest() {
        assignmentFound();
        lockReturnsAssignment();

        assertThatThrownBy(() -> service.reportIssue(sessionId, attemptId, issueRequest(0), examiner))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).isEqualTo(ExaminerScoringConstants.WORK_NOT_STARTED_FOR_ISSUES);
                });
    }

    @Test
    void reportIssue_onFinalizedWork_isConflict() {
        assignment.start(NOW.minusSeconds(60));
        assignment.finalizeGrading(NOW.minusSeconds(30));
        assignmentFound();
        lockReturnsAssignment();

        assertThatThrownBy(() -> service.reportIssue(sessionId, attemptId, issueRequest(2), examiner))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo(ExaminerScoringConstants.WORK_FINALIZED_NO_ISSUES);
                });
    }

    @Test
    void reportIssue_staleWorkVersion_isConflict() {
        assignment.start(NOW.minusSeconds(60));
        assignmentFound();
        lockReturnsAssignment();

        assertThatThrownBy(() -> service.reportIssue(sessionId, attemptId, issueRequest(9), examiner))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(ExaminerScoringConstants.WORK_VERSION_MISMATCH));
        verify(issueRepository, never()).save(any());
    }

    @Test
    void reportIssue_matchingVersion_savesTrimmedIssueAndReturnsReceipt() {
        assignment.start(NOW.minusSeconds(60));
        assignmentFound();
        lockReturnsAssignment();
        noReplay();
        ReportGradingIssueRequest request = issueRequest(1);

        ExaminerCommandResponse response = service.reportIssue(sessionId, attemptId, request, examiner);

        org.mockito.ArgumentCaptor<GradingIssue> saved = org.mockito.ArgumentCaptor.forClass(GradingIssue.class);
        verify(issueRepository).save(saved.capture());
        assertThat(saved.getValue().getDescription()).isEqualTo("No sound");
        assertThat(saved.getValue().getType()).isEqualTo(ExaminerGradingIssueType.AUDIO_UNAVAILABLE);
        assertThat(saved.getValue().getReportedBy()).isEqualTo(examiner.userId());
        assertThat(response.receipt().operation()).isEqualTo(ExaminerGradingOperation.REPORT_ISSUE);
        assertThat(response.receipt().answerPublicId()).isEqualTo(request.answerPublicId());
    }

    @Test
    void reportIssue_repeatedRequest_doesNotSaveASecondIssue() {
        assignment.start(NOW.minusSeconds(60));
        assignmentFound();
        lockReturnsAssignment();
        ExaminerCommandReceiptResponse stored = new ExaminerCommandReceiptResponse(UUID.randomUUID(),
                ExaminerGradingOperation.REPORT_ISSUE, NOW, 1L, null, null, Collections.emptyList());
        when(recorder.replayOrBegin(eq(examiner), eq(assignment.getPublicId()), any(UUID.class), anyString(), eq(NOW)))
                .thenReturn(Optional.of(stored));

        assertThat(service.reportIssue(sessionId, attemptId, issueRequest(1), examiner).receipt()).isSameAs(stored);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void resolveIssue_unknownIssue_isNotFound() {
        UUID issueId = UUID.randomUUID();
        when(issueRepository.findByTenantIdAndPublicIdAndDeletedFalse(tenantId, issueId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveIssue(issueId, new ResolveGradingIssueRequest(UUID.randomUUID(), "ok"),
                examiner)).isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo(ExaminerScoringConstants.GRADING_ISSUE_NOT_FOUND);
                });
    }

    @Test
    void resolveIssue_openIssue_isResolvedWithTrimmedText() {
        GradingIssue issue = new GradingIssue(tenantId, 7L, UUID.randomUUID(),
                ExaminerGradingIssueType.OTHER, "desc", examiner.userId(), NOW.minusSeconds(30));
        issue.setPublicId(UUID.randomUUID());
        when(issueRepository.findByTenantIdAndPublicIdAndDeletedFalse(tenantId, issue.getPublicId()))
                .thenReturn(Optional.of(issue));
        when(assignmentRepository.findById(7L)).thenReturn(Optional.of(assignment));

        ExaminerGradingIssueResponse response = service.resolveIssue(issue.getPublicId(),
                new ResolveGradingIssueRequest(UUID.randomUUID(), "  Re-uploaded  "), examiner);

        assertThat(response.resolution()).isEqualTo("Re-uploaded");
        assertThat(response.resolvedAt()).isEqualTo(NOW);
        assertThat(response.sessionPublicId()).isEqualTo(sessionId);
        verify(issueRepository).save(issue);
        verify(recorder).record(eq(examiner), eq(assignment.getPublicId()), isNull(), anyString(), any());
    }

    @Test
    void resolveIssue_issueWhoseAssignmentVanished_isNotFound() {
        GradingIssue issue = new GradingIssue(tenantId, 8L, null, ExaminerGradingIssueType.OTHER, "d",
                examiner.userId(), NOW);
        issue.setPublicId(UUID.randomUUID());
        when(issueRepository.findByTenantIdAndPublicIdAndDeletedFalse(tenantId, issue.getPublicId()))
                .thenReturn(Optional.of(issue));
        when(assignmentRepository.findById(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveIssue(issue.getPublicId(),
                new ResolveGradingIssueRequest(UUID.randomUUID(), "x"), examiner))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(ExaminerScoringConstants.ASSIGNMENT_OF_ISSUE_NOT_FOUND));
    }
}
