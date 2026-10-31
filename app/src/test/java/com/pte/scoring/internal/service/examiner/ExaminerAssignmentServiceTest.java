package com.pte.scoring.internal.service.examiner;

import com.pte.scoring.internal.service.grading.GradingCohortService;
import com.pte.scoring.internal.service.grading.SessionGradingGateway;
import com.pte.scoring.internal.service.selection.ScorePublicationLockService;
import com.pte.scoring.internal.service.staff.ScoringEligibilityQueryService;
import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptGradingCandidateView;
import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.identity.IdentityService;
import com.pte.identity.dto.response.ExaminerIdentityView;
import com.pte.identity.dto.response.StudentDisplayNameView;
import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.scoring.domain.GradingAssignment;
import com.pte.scoring.domain.GradingIssue;
import com.pte.scoring.domain.enums.ExaminerDecisionKind;
import com.pte.scoring.domain.enums.ExaminerGradingIssueStatus;
import com.pte.scoring.domain.enums.ExaminerGradingIssueType;
import com.pte.scoring.domain.enums.ExaminerGradingOperation;
import com.pte.scoring.domain.enums.ExaminerWorkflowStatus;
import com.pte.scoring.domain.enums.GradingCohortStatus;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.dto.request.CommitExaminerAssignmentRequest;
import com.pte.scoring.dto.request.ReassignExaminerRequest;
import com.pte.scoring.dto.response.AiEligibleAttemptView;
import com.pte.scoring.internal.repository.ExaminerEvaluationRepository;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.scoring.internal.repository.GradingIssueRepository;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The host's one-shot assignment table: roster, full commit, and the examiner load overview. */
@ExtendWith(MockitoExtension.class)
class ExaminerAssignmentServiceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID examiner1 = UUID.randomUUID();
    private final UUID examiner2 = UUID.randomUUID();
    private final CurrentUser host = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));

    @Mock private SessionService sessionService;
    @Mock private AttemptService attemptService;
    @Mock private ScoringEligibilityQueryService eligibilityService;
    @Mock private IdentityService identityService;
    @Mock private GradingAssignmentRepository assignmentRepository;
    @Mock private ExaminerEvaluationRepository evaluationRepository;
    @Mock private GradingIssueRepository issueRepository;
    @Mock private ExaminerWorkRecorder recorder;
    @Mock private ScorePublicationLockService publicationLockService;
    @Mock private SessionGradingGateway gateway;
    @Mock private GradingCohortService cohortService;

    private ExaminerAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new ExaminerAssignmentService(sessionService, attemptService, eligibilityService, identityService,
                assignmentRepository, evaluationRepository, issueRepository, recorder, publicationLockService, gateway,
                cohortService, com.pte.TestClocks.fixed());
    }

    @Test
    void nonHostCannotReadOrCommitTheAssignment() {
        CurrentUser student = new CurrentUser(UUID.randomUUID(), tenantId, List.of("STUDENT"));

        assertThatThrownBy(() -> service.roster(sessionId, student)).hasMessage("A tenant HOST_ADMIN is required.");
        assertThatThrownBy(() -> service.commit(sessionId, new CommitExaminerAssignmentRequest(List.of()), student))
                .hasMessage("A tenant HOST_ADMIN is required.");
        verify(sessionService, never()).lockForExaminerAssignment(any(), any());
    }

    @Test
    void rosterListsEveryEligibleAttemptUnassignedAndUnlockedBeforeCommit() {
        UUID studentA = UUID.randomUUID();
        UUID studentB = UUID.randomUUID();
        var attemptA = new AttemptSummaryView(UUID.randomUUID(), sessionId, studentA, tenantId);
        var attemptB = new AttemptSummaryView(UUID.randomUUID(), sessionId, studentB, tenantId);
        stubRosterPool(List.of(attemptA, attemptB));
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of());
        when(identityService.getStudentDisplayNames(any(), any())).thenReturn(Map.of(
                studentA, new StudentDisplayNameView(studentA, "Zed", "zed@example.test"),
                studentB, new StudentDisplayNameView(studentB, "Amy", "amy@example.test")));

        var roster = service.roster(sessionId, host);

        assertThat(roster.locked()).isFalse();
        assertThat(roster.rows()).extracting(row -> row.studentName()).containsExactly("Amy", "Zed");
        assertThat(roster.rows()).extracting(row -> row.examinerPublicId()).containsOnlyNulls();
        verify(sessionService).verifyHostAccess(sessionId, tenantId);
    }

    @Test
    void rosterLeavesOutAttemptsTheHostExcludedFromGrading() {
        var kept = new AttemptSummaryView(UUID.randomUUID(), sessionId, UUID.randomUUID(), tenantId);
        var excluded = new AttemptSummaryView(UUID.randomUUID(), sessionId, UUID.randomUUID(), tenantId);
        when(attemptService.getSubmittedAttemptsForSession(sessionId, tenantId)).thenReturn(List.of(kept, excluded));
        when(attemptService.getGradingCandidatesForSession(sessionId, tenantId)).thenReturn(List.of(
                candidate(kept.attemptPublicId(), false), candidate(excluded.attemptPublicId(), true)));
        when(eligibilityService.findEligibleAttempts(sessionId, tenantId, List.of(kept.attemptPublicId())))
                .thenReturn(List.of(new AiEligibleAttemptView(kept.attemptPublicId(), 1)));
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of());
        when(identityService.getStudentDisplayNames(any(), any())).thenReturn(Map.of());

        var roster = service.roster(sessionId, host);

        assertThat(roster.rows()).extracting(row -> row.attemptPublicId()).containsExactly(kept.attemptPublicId());
    }

    @Test
    void rosterIsLockedAndShowsCommittedExaminersOnceAssigned() {
        var attempt = new AttemptSummaryView(UUID.randomUUID(), sessionId, UUID.randomUUID(), tenantId);
        when(attemptService.getSubmittedAttemptsForSession(sessionId, tenantId)).thenReturn(List.of(attempt));
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of(assignment(attempt.attemptPublicId(), examiner1)));
        when(identityService.getStudentDisplayNames(any(), any())).thenReturn(Map.of());

        var roster = service.roster(sessionId, host);

        assertThat(roster.locked()).isTrue();
        assertThat(roster.rows()).singleElement()
                .satisfies(row -> assertThat(row.examinerPublicId()).isEqualTo(examiner1));
    }

    @Test
    void commitCreatesOneGradingAssignmentPerAttemptAndFreezesTheManualGrading() {
        var attemptA = new AttemptSummaryView(UUID.randomUUID(), sessionId, UUID.randomUUID(), tenantId);
        var attemptB = new AttemptSummaryView(UUID.randomUUID(), sessionId, UUID.randomUUID(), tenantId);
        stubRosterPool(List.of(attemptA, attemptB));
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of());
        when(gateway.grading(tenantId, sessionId)).thenReturn(Optional.empty());
        when(identityService.lockActiveExaminers(eq(tenantId), any()))
                .thenReturn(List.of(examiner(examiner1), examiner(examiner2)));
        when(identityService.getStudentDisplayNames(any(), any())).thenReturn(Map.of());

        service.commit(sessionId, new CommitExaminerAssignmentRequest(List.of(
                new CommitExaminerAssignmentRequest.Entry(attemptA.attemptPublicId(), examiner1),
                new CommitExaminerAssignmentRequest.Entry(attemptB.attemptPublicId(), examiner2))), host);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<GradingAssignment>> rows = ArgumentCaptor.forClass(Iterable.class);
        verify(assignmentRepository).saveAll(rows.capture());
        assertThat(rows.getValue()).extracting(GradingAssignment::getExaminerPublicId)
                .containsExactlyInAnyOrder(examiner1, examiner2);
        assertThat(rows.getValue()).allSatisfy(row -> {
            assertThat(row.isStarted()).isFalse();
            assertThat(row.getEligibleAnswerCount()).isEqualTo(1);
            assertThat(row.getAssignedByPublicId()).isEqualTo(host.userId());
        });
        verify(publicationLockService).assertNotPublished(tenantId, sessionId);
        verify(cohortService).ensureManualCohort(sessionId, tenantId, host.userId());
    }

    @Test
    void commitRejectsATableThatLeavesAnEligibleAttemptUnassigned() {
        var attemptA = new AttemptSummaryView(UUID.randomUUID(), sessionId, UUID.randomUUID(), tenantId);
        var attemptB = new AttemptSummaryView(UUID.randomUUID(), sessionId, UUID.randomUUID(), tenantId);
        stubRosterPool(List.of(attemptA, attemptB));
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of());
        when(gateway.grading(tenantId, sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.commit(sessionId, new CommitExaminerAssignmentRequest(List.of(
                new CommitExaminerAssignmentRequest.Entry(attemptA.attemptPublicId(), examiner1))), host))
                .hasMessage("Assign every eligible attempt to exactly one Examiner.");
        verify(assignmentRepository, never()).saveAll(any());
    }

    @Test
    void commitRejectsAnAttemptListedTwice() {
        UUID attemptId = UUID.randomUUID();
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of());
        when(gateway.grading(tenantId, sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.commit(sessionId, new CommitExaminerAssignmentRequest(List.of(
                new CommitExaminerAssignmentRequest.Entry(attemptId, examiner1),
                new CommitExaminerAssignmentRequest.Entry(attemptId, examiner2))), host))
                .hasMessage("Assign every eligible attempt to exactly one Examiner.");
    }

    @Test
    void commitRejectsOnceAssignmentIsAlreadyLocked() {
        UUID attemptId = UUID.randomUUID();
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of(assignment(attemptId, examiner1)));

        assertThatThrownBy(() -> service.commit(sessionId, new CommitExaminerAssignmentRequest(List.of(
                new CommitExaminerAssignmentRequest.Entry(attemptId, examiner2))), host))
                .hasMessage("Examiners are already assigned for this exam.");
        verify(assignmentRepository, never()).saveAll(any());
    }

    @Test
    void commitRejectsAnInactiveExaminer() {
        var attempt = new AttemptSummaryView(UUID.randomUUID(), sessionId, UUID.randomUUID(), tenantId);
        stubRosterPool(List.of(attempt));
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of());
        when(gateway.grading(tenantId, sessionId)).thenReturn(Optional.empty());
        when(identityService.lockActiveExaminers(eq(tenantId), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.commit(sessionId, new CommitExaminerAssignmentRequest(List.of(
                new CommitExaminerAssignmentRequest.Entry(attempt.attemptPublicId(), examiner1))), host))
                .hasMessage("Every selected Examiner must be active and belong to this tenant.");
        verify(assignmentRepository, never()).saveAll(any());
    }

    @Test
    void commitIsRefusedWhenTheSessionIsGradedByAiOnly() {
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of());
        when(gateway.grading(tenantId, sessionId)).thenReturn(Optional.of(new SessionGradingGateway.Grading(
                sessionId, tenantId, GradingMarkingMode.AI_ONLY, GradingCohortStatus.FROZEN, "v1", host.userId(),
                Instant.now(), null)));

        assertThatThrownBy(() -> service.commit(sessionId, new CommitExaminerAssignmentRequest(List.of(
                new CommitExaminerAssignmentRequest.Entry(UUID.randomUUID(), examiner1))), host))
                .hasMessageContaining("AI");
        verify(assignmentRepository, never()).saveAll(any());
    }

    @Test
    void overviewSumsTheCommittedLoadOfEachExaminer() {
        var load = new FakeLoad(examiner1, 2L, 7L);
        when(assignmentRepository.summarizeLoads(tenantId, sessionId)).thenReturn(List.of(load));
        when(assignmentRepository.countByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId)).thenReturn(2L);

        var overview = service.overview(sessionId, host);

        assertThat(overview.assignedAttemptCount()).isEqualTo(2L);
        assertThat(overview.committedExaminerLoads()).singleElement().satisfies(item -> {
            assertThat(item.examinerPublicId()).isEqualTo(examiner1);
            assertThat(item.attemptCount()).isEqualTo(2L);
            assertThat(item.eligibleAnswerCount()).isEqualTo(7L);
        });
        verify(sessionService).verifyHostAccess(sessionId, tenantId);
    }

    @Test
    void rosterMarksAnUnfinishedCommittedAttemptReassignableAndShowsItsDrafts() {
        var attempt = new AttemptSummaryView(UUID.randomUUID(), sessionId, UUID.randomUUID(), tenantId);
        GradingAssignment work = assignment(attempt.attemptPublicId(), examiner1);
        when(attemptService.getSubmittedAttemptsForSession(sessionId, tenantId)).thenReturn(List.of(attempt));
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of(work));
        when(identityService.getStudentDisplayNames(any(), any())).thenReturn(Map.of());

        var roster = service.roster(sessionId, host);

        assertThat(roster.rows()).singleElement().satisfies(row -> {
            assertThat(row.workStatus()).isEqualTo(ExaminerWorkflowStatus.PENDING);
            assertThat(row.reassignable()).isTrue();
        });
    }

    @Test
    void rosterDoesNotOfferReassignmentOnceTheExaminerFinalizedOrScoresArePublished() {
        var attempt = new AttemptSummaryView(UUID.randomUUID(), sessionId, UUID.randomUUID(), tenantId);
        GradingAssignment work = assignment(attempt.attemptPublicId(), examiner1);
        work.start(Instant.now());
        work.finalizeGrading(Instant.now());
        when(attemptService.getSubmittedAttemptsForSession(sessionId, tenantId)).thenReturn(List.of(attempt));
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of(work));
        when(identityService.getStudentDisplayNames(any(), any())).thenReturn(Map.of());

        assertThat(service.roster(sessionId, host).rows()).singleElement()
                .satisfies(row -> assertThat(row.reassignable()).isFalse());

        GradingAssignment open = assignment(attempt.attemptPublicId(), examiner1);
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of(open));
        when(gateway.isPublished(tenantId, sessionId)).thenReturn(true);

        assertThat(service.roster(sessionId, host).rows()).singleElement()
                .satisfies(row -> assertThat(row.reassignable()).isFalse());
    }

    @Test
    void reassignCarriesDraftsToTheNewExaminerRestartsTheWorkAndClosesOpenIssues() {
        UUID attemptId = UUID.randomUUID();
        GradingAssignment work = startedWork(attemptId, examiner1);
        long versionBefore = work.getWorkVersion();
        ExaminerEvaluation draft = draft(attemptId, examiner1, 70);
        GradingIssue issue = new GradingIssue(tenantId, 5L, UUID.randomUUID(), ExaminerGradingIssueType.AUDIO_UNAVAILABLE,
                "no audio", examiner1, Instant.now());
        stubReassign(attemptId, work, List.of(draft));
        when(issueRepository.findByTenantIdAndAssignmentIdAndDeletedFalseOrderByReportedAtAsc(any(), any()))
                .thenReturn(List.of(issue));
        when(identityService.lockActiveExaminers(eq(tenantId), any())).thenReturn(List.of(examiner(examiner2)));

        service.reassign(sessionId, attemptId, new ReassignExaminerRequest(examiner2), host);

        assertThat(work.getExaminerPublicId()).isEqualTo(examiner2);
        assertThat(work.getStatus()).isEqualTo(ExaminerWorkflowStatus.PENDING);
        assertThat(work.getStartedAt()).isNull();
        assertThat(work.getWorkVersion()).isGreaterThan(versionBefore);
        assertThat(draft.getExaminerPublicId()).isEqualTo(examiner2);
        assertThat(draft.isCarriedOver()).isTrue();
        assertThat(draft.getScore()).isEqualTo(70);
        assertThat(issue.getStatus()).isEqualTo(ExaminerGradingIssueStatus.RESOLVED);
        ArgumentCaptor<ExaminerWorkRecorder.Entry> entry = ArgumentCaptor.forClass(ExaminerWorkRecorder.Entry.class);
        verify(recorder).record(eq(host), eq(work.getPublicId()), any(), any(), entry.capture());
        assertThat(entry.getValue().operation()).isEqualTo(ExaminerGradingOperation.REASSIGN);
        assertThat(entry.getValue().metadata().reason()).contains("1 drafts carried over", "70");
        verify(sessionService).lockForScoreReviewMutation(sessionId, tenantId);
    }

    @Test
    void reassignRefusesFinalizedWorkTheSameExaminerAndInactiveExaminers() {
        UUID attemptId = UUID.randomUUID();
        GradingAssignment finalized = startedWork(attemptId, examiner1);
        finalized.finalizeGrading(Instant.now());
        stubReassign(attemptId, finalized, List.of());
        assertThatThrownBy(() -> service.reassign(sessionId, attemptId, new ReassignExaminerRequest(examiner2), host))
                .hasMessageContaining("can no longer be reassigned");

        GradingAssignment open = startedWork(attemptId, examiner1);
        stubReassign(attemptId, open, List.of());
        assertThatThrownBy(() -> service.reassign(sessionId, attemptId, new ReassignExaminerRequest(examiner1), host))
                .hasMessageContaining("already assigned to this Examiner");

        when(identityService.lockActiveExaminers(eq(tenantId), any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.reassign(sessionId, attemptId, new ReassignExaminerRequest(examiner2), host))
                .hasMessage("Every selected Examiner must be active and belong to this tenant.");
        assertThat(open.getExaminerPublicId()).isEqualTo(examiner1);
        verify(recorder, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void reassignRefusesAnAttemptWithNoCommittedAssignmentAndNonHosts() {
        UUID attemptId = UUID.randomUUID();
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndAttemptPublicIdAndDeletedFalse(tenantId,
                sessionId, attemptId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reassign(sessionId, attemptId, new ReassignExaminerRequest(examiner2), host))
                .hasMessage("This attempt has no Examiner assignment.");
        CurrentUser student = new CurrentUser(UUID.randomUUID(), tenantId, List.of("STUDENT"));
        assertThatThrownBy(() -> service.reassign(sessionId, attemptId, new ReassignExaminerRequest(examiner2), student))
                .hasMessage("A tenant HOST_ADMIN is required.");
    }

    private void stubReassign(UUID attemptId, GradingAssignment work, List<ExaminerEvaluation> drafts) {
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndAttemptPublicIdAndDeletedFalse(tenantId,
                sessionId, attemptId)).thenReturn(Optional.of(work));
        when(assignmentRepository.findForUpdate(tenantId, work.getPublicId())).thenReturn(Optional.of(work));
        when(evaluationRepository.findByTenantIdAndAssignmentIdAndDeletedFalse(any(), any())).thenReturn(drafts);
    }

    private GradingAssignment startedWork(UUID attemptId, UUID examinerId) {
        GradingAssignment work = assignment(attemptId, examinerId);
        work.start(Instant.now());
        return work;
    }

    private ExaminerEvaluation draft(UUID attemptId, UUID examinerId, int score) {
        return ExaminerEvaluation.draft(tenantId, sessionId, attemptId, UUID.randomUUID(), examinerId, 1L, score,
                null, ExaminerDecisionKind.MANUAL, "rv", null, Instant.now());
    }

    private void stubRosterPool(List<AttemptSummaryView> attempts) {
        when(attemptService.getSubmittedAttemptsForSession(sessionId, tenantId)).thenReturn(attempts);
        when(attemptService.getGradingCandidatesForSession(sessionId, tenantId)).thenReturn(List.of());
        when(eligibilityService.findEligibleAttempts(sessionId, tenantId,
                attempts.stream().map(AttemptSummaryView::attemptPublicId).toList()))
                .thenReturn(attempts.stream().map(a -> new AiEligibleAttemptView(a.attemptPublicId(), 1)).toList());
    }

    private GradingAssignment assignment(UUID attemptId, UUID examinerId) {
        return new GradingAssignment(tenantId, sessionId, attemptId, examinerId, 1, host.userId(), Instant.now());
    }

    private AttemptGradingCandidateView candidate(UUID attemptId, boolean excluded) {
        return new AttemptGradingCandidateView(attemptId, UUID.randomUUID(), "SUBMITTED", "v", excluded,
                excluded ? "withdrawn" : null);
    }

    private ExaminerIdentityView examiner(UUID id) {
        return new ExaminerIdentityView(id, "Examiner", "examiner@example.test");
    }

    private record FakeLoad(UUID examiner, long attempts, long answers)
            implements GradingAssignmentRepository.ExaminerAssignmentLoadProjection {
        @Override
        public UUID getExaminerPublicId() {
            return examiner;
        }

        @Override
        public long getAttemptCount() {
            return attempts;
        }

        @Override
        public long getEligibleAnswerCount() {
            return answers;
        }
    }
}
