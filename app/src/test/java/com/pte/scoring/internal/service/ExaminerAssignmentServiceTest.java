package com.pte.scoring.internal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.enrollment.EnrollmentModuleService;
import com.pte.identity.IdentityService;
import com.pte.identity.dto.response.ExaminerIdentityView;
import com.pte.scoring.domain.ExaminerAssignmentBatch;
import com.pte.scoring.domain.ExaminerAttemptAssignment;
import com.pte.scoring.domain.enums.AssignmentBatchMode;
import com.pte.scoring.domain.enums.AssignmentScopeType;
import com.pte.scoring.dto.request.CreateExaminerAssignmentPreviewRequest;
import com.pte.scoring.dto.request.CreateExaminerAssignmentPreviewRequest.AssignmentScopeRequest;
import com.pte.scoring.dto.response.AiEligibleAttemptView;
import com.pte.scoring.domain.enums.AssignmentBatchStatus;
import com.pte.scoring.internal.repository.ExaminerAssignmentBatchRepository;
import com.pte.scoring.internal.repository.ExaminerAttemptAssignmentRepository;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExaminerAssignmentServiceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID examiner1 = UUID.randomUUID();
    private final UUID examiner2 = UUID.randomUUID();
    private final UUID batchId = UUID.randomUUID();
    private final CurrentUser host = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));

    @Mock private SessionService sessionService;
    @Mock private EnrollmentModuleService enrollmentService;
    @Mock private AttemptService attemptService;
    @Mock private ScoringEligibilityQueryService eligibilityService;
    @Mock private IdentityService identityService;
    @Mock private ExaminerAssignmentBatchRepository batchRepository;
    @Mock private ExaminerAttemptAssignmentRepository assignmentRepository;

    private ExaminerAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new ExaminerAssignmentService(sessionService, enrollmentService, attemptService, eligibilityService,
                identityService, batchRepository, assignmentRepository, new ObjectMapper());
    }

    @Test
    void randomPreviewPersistsExactBalancedAllocationAndConfirmRetryDoesNotDuplicateRows() {
        List<UUID> students = ids(40);
        List<AttemptSummaryView> attempts = new ArrayList<>();
        for (int i = 0; i < students.size(); i++) {
            attempts.add(new AttemptSummaryView(UUID.randomUUID(), sessionId, students.get(i), tenantId));
        }
        setCandidatePool(students, attempts);
        when(identityService.findActiveExaminers(tenantId, List.of(examiner1, examiner2)))
                .thenReturn(List.of(examiner(examiner1), examiner(examiner2)));
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of());
        stubBatchSave();
        CreateExaminerAssignmentPreviewRequest request = new CreateExaminerAssignmentPreviewRequest(
                AssignmentBatchMode.RANDOM,
                List.of(new AssignmentScopeRequest(AssignmentScopeType.CLASS, classId, null)),
                List.of(examiner1, examiner2));

        var preview = service.preview(sessionId, request, host);
        ArgumentCaptor<ExaminerAssignmentBatch> batchCaptor = ArgumentCaptor.forClass(ExaminerAssignmentBatch.class);
        verify(batchRepository).saveAndFlush(batchCaptor.capture());
        ExaminerAssignmentBatch batch = batchCaptor.getValue();
        when(batchRepository.findForUpdate(batchId, tenantId, sessionId)).thenReturn(Optional.of(batch));
        when(identityService.lockActiveExaminers(tenantId, List.of(examiner1, examiner2)))
                .thenReturn(List.of(examiner(examiner1), examiner(examiner2)));
        var confirmOnce = service.confirm(sessionId, batchId, host);
        var confirmRetry = service.confirm(sessionId, batchId, host);

        assertThat(preview.valid()).isTrue();
        assertThat(preview.attemptCount()).isEqualTo(40);
        assertThat(preview.examinerLoads()).extracting(load -> load.attemptCount()).containsExactly(20L, 20L);
        assertThat(batch.getRandomSeed()).isNotNull();
        assertThat(batch.getAssignmentSnapshotJson()).contains("attemptPublicId", "examinerPublicId");
        assertThat(confirmOnce.status()).isEqualTo("COMMITTED");
        assertThat(confirmRetry.status()).isEqualTo("COMMITTED");
        verify(assignmentRepository).saveAll(any());
        verify(assignmentRepository).flush();
    }

    @Test
    void manualOverlapAssignedToDifferentExaminersReturnsConflictWithoutPersistingPreview() {
        UUID student = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        when(enrollmentService.findActiveStudentPublicIds(tenantId, classId)).thenReturn(List.of(student));
        when(enrollmentService.findActiveStudentPublicIdsByProgram(tenantId, programId)).thenReturn(List.of(student));
        when(attemptService.getSubmittedAttemptsForSession(sessionId, tenantId)).thenReturn(
                List.of(new AttemptSummaryView(attemptId, sessionId, student, tenantId)));
        when(eligibilityService.findEligibleAttempts(sessionId, tenantId, List.of(attemptId)))
                .thenReturn(List.of(new AiEligibleAttemptView(attemptId, 2)));
        when(identityService.findActiveExaminers(tenantId, List.of(examiner1, examiner2)))
                .thenReturn(List.of(examiner(examiner1), examiner(examiner2)));
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of());
        var request = new CreateExaminerAssignmentPreviewRequest(AssignmentBatchMode.MANUAL, List.of(
                new AssignmentScopeRequest(AssignmentScopeType.CLASS, classId, examiner1),
                new AssignmentScopeRequest(AssignmentScopeType.PROGRAM, programId, examiner2)), List.of());

        var result = service.preview(sessionId, request, host);

        assertThat(result.valid()).isFalse();
        assertThat(result.batchPublicId()).isNull();
        assertThat(result.conflicts()).singleElement().satisfies(conflict ->
                assertThat(conflict.attemptPublicId()).isEqualTo(attemptId));
        verify(batchRepository, never()).saveAndFlush(any());
    }

    @Test
    void confirmMarksPreviewStaleWhenMembershipChangesAfterPreview() {
        UUID student = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        setCandidatePool(List.of(student), List.of(new AttemptSummaryView(attemptId, sessionId, student, tenantId)));
        when(identityService.findActiveExaminers(tenantId, List.of(examiner1)))
                .thenReturn(List.of(examiner(examiner1)));
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of());
        when(enrollmentService.findActiveStudentPublicIds(tenantId, classId))
                .thenReturn(List.of(student), List.of(UUID.randomUUID()));
        stubBatchSave();
        var request = new CreateExaminerAssignmentPreviewRequest(AssignmentBatchMode.MANUAL,
                List.of(new AssignmentScopeRequest(AssignmentScopeType.CLASS, classId, examiner1)), List.of());

        service.preview(sessionId, request, host);
        ArgumentCaptor<ExaminerAssignmentBatch> batchCaptor = ArgumentCaptor.forClass(ExaminerAssignmentBatch.class);
        verify(batchRepository).saveAndFlush(batchCaptor.capture());
        when(batchRepository.findForUpdate(batchId, tenantId, sessionId)).thenReturn(Optional.of(batchCaptor.getValue()));

        var result = service.confirm(sessionId, batchId, host);

        assertThat(result.status()).isEqualTo("STALE");
        assertThat(result.valid()).isFalse();
        assertThat(batchCaptor.getValue().getStatus()).isEqualTo(AssignmentBatchStatus.STALE);
        verify(batchRepository, org.mockito.Mockito.times(2)).saveAndFlush(any(ExaminerAssignmentBatch.class));
        verify(assignmentRepository, never()).saveAll(any());
    }

    @Test
    void confirmMarksPreviewStaleWhenExaminerEligibilityChangesBeforeCommit() {
        UUID student = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        setCandidatePool(List.of(student), List.of(new AttemptSummaryView(attemptId, sessionId, student, tenantId)));
        when(identityService.findActiveExaminers(tenantId, List.of(examiner1)))
                .thenReturn(List.of(examiner(examiner1)));
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of());
        stubBatchSave();
        var request = new CreateExaminerAssignmentPreviewRequest(AssignmentBatchMode.RANDOM,
                List.of(new AssignmentScopeRequest(AssignmentScopeType.CLASS, classId, null)), List.of(examiner1));

        service.preview(sessionId, request, host);
        ArgumentCaptor<ExaminerAssignmentBatch> batchCaptor = ArgumentCaptor.forClass(ExaminerAssignmentBatch.class);
        verify(batchRepository).saveAndFlush(batchCaptor.capture());
        when(batchRepository.findForUpdate(batchId, tenantId, sessionId))
                .thenReturn(Optional.of(batchCaptor.getValue()));
        when(identityService.lockActiveExaminers(tenantId, List.of(examiner1))).thenReturn(List.of());

        var result = service.confirm(sessionId, batchId, host);

        assertThat(result.status()).isEqualTo("STALE");
        assertThat(result.valid()).isFalse();
        assertThat(batchCaptor.getValue().getStatus()).isEqualTo(AssignmentBatchStatus.STALE);
        assertThat(service.confirm(sessionId, batchId, host).status()).isEqualTo("STALE");
        verify(assignmentRepository, never()).saveAll(any());
    }

    @Test
    void confirmMarksPreviewStaleWhenExaminerWasDeactivatedBeforeConfirmation() {
        UUID student = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        setCandidatePool(List.of(student), List.of(new AttemptSummaryView(attemptId, sessionId, student, tenantId)));
        when(identityService.findActiveExaminers(tenantId, List.of(examiner1)))
                .thenReturn(List.of(examiner(examiner1)), List.of());
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of());
        when(identityService.lockActiveExaminers(tenantId, List.of(examiner1))).thenReturn(List.of());
        stubBatchSave();
        var request = new CreateExaminerAssignmentPreviewRequest(AssignmentBatchMode.RANDOM,
                List.of(new AssignmentScopeRequest(AssignmentScopeType.CLASS, classId, null)), List.of(examiner1));

        service.preview(sessionId, request, host);
        ArgumentCaptor<ExaminerAssignmentBatch> batchCaptor = ArgumentCaptor.forClass(ExaminerAssignmentBatch.class);
        verify(batchRepository).saveAndFlush(batchCaptor.capture());
        when(batchRepository.findForUpdate(batchId, tenantId, sessionId))
                .thenReturn(Optional.of(batchCaptor.getValue()));

        var result = service.confirm(sessionId, batchId, host);

        assertThat(result.status()).isEqualTo("STALE");
        assertThat(batchCaptor.getValue().getStatus()).isEqualTo(AssignmentBatchStatus.STALE);
        verify(assignmentRepository, never()).saveAll(any());
    }

    @Test
    void confirmMarksRandomPreviewStaleWhenEligibleAnswerCountChanges() {
        UUID student = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        List<AttemptSummaryView> attempts =
                List.of(new AttemptSummaryView(attemptId, sessionId, student, tenantId));
        setCandidatePool(List.of(student), attempts);
        when(eligibilityService.findEligibleAttempts(sessionId, tenantId, List.of(attemptId)))
                .thenReturn(List.of(new AiEligibleAttemptView(attemptId, 2)),
                        List.of(new AiEligibleAttemptView(attemptId, 3)));
        when(identityService.findActiveExaminers(tenantId, List.of(examiner1)))
                .thenReturn(List.of(examiner(examiner1)));
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of());
        stubBatchSave();
        var request = new CreateExaminerAssignmentPreviewRequest(AssignmentBatchMode.RANDOM,
                List.of(new AssignmentScopeRequest(AssignmentScopeType.CLASS, classId, null)), List.of(examiner1));

        service.preview(sessionId, request, host);
        ArgumentCaptor<ExaminerAssignmentBatch> batchCaptor = ArgumentCaptor.forClass(ExaminerAssignmentBatch.class);
        verify(batchRepository).saveAndFlush(batchCaptor.capture());
        when(batchRepository.findForUpdate(batchId, tenantId, sessionId))
                .thenReturn(Optional.of(batchCaptor.getValue()));

        var result = service.confirm(sessionId, batchId, host);

        assertThat(result.status()).isEqualTo("STALE");
        assertThat(batchCaptor.getValue().getStatus()).isEqualTo(AssignmentBatchStatus.STALE);
        verify(assignmentRepository, never()).saveAll(any());
    }

    @Test
    void overviewReturnsOnlyRequestedHistoryPageAndPersistsPreviewExpiry() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        UUID attemptId = UUID.randomUUID();
        var scopeSnapshot = new ExaminerAssignmentService.ScopeSnapshotEnvelope(List.of(
                new ExaminerAssignmentService.ScopeSnapshot(AssignmentScopeType.CLASS, classId, examiner1,
                        List.of(attemptId))), List.of(examiner1), false, 1);
        var assignmentEntry = new ExaminerAssignmentService.AssignmentEntry(attemptId, examiner1, 1);
        var batch = new ExaminerAssignmentBatch(tenantId, sessionId, host.userId(), AssignmentBatchMode.MANUAL,
                mapper.writeValueAsString(scopeSnapshot), mapper.writeValueAsString(List.of(assignmentEntry)), null,
                java.time.Instant.now().plusSeconds(60));
        batch.setPublicId(batchId);
        when(batchRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(
                eq(tenantId), eq(sessionId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(batch), PageRequest.of(1, 1), 3));

        var result = service.overview(sessionId, host, 1, 1);

        assertThat(result.batches()).hasSize(1);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.totalBatches()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(3);
        verify(batchRepository).expireOverduePreviews(eq(tenantId), eq(sessionId),
                eq(AssignmentBatchStatus.PREVIEWED), eq(AssignmentBatchStatus.EXPIRED),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void supplementalPreviewIncludesOnlyUnassignedSubmittedAttempts() {
        UUID student1 = UUID.randomUUID();
        UUID student2 = UUID.randomUUID();
        UUID attempt1 = UUID.randomUUID();
        UUID attempt2 = UUID.randomUUID();
        List<AttemptSummaryView> attempts = List.of(
                new AttemptSummaryView(attempt1, sessionId, student1, tenantId),
                new AttemptSummaryView(attempt2, sessionId, student2, tenantId));
        setCandidatePool(List.of(student1, student2), attempts);
        when(identityService.findActiveExaminers(tenantId, List.of(examiner1)))
                .thenReturn(List.of(examiner(examiner1)));
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId)).thenReturn(List.of(
                new ExaminerAttemptAssignment(UUID.randomUUID(), tenantId, sessionId, attempt1, examiner2, 1,
                        host.userId(), java.time.Instant.now())));
        stubBatchSave();
        var request = new CreateExaminerAssignmentPreviewRequest(AssignmentBatchMode.RANDOM,
                List.of(new AssignmentScopeRequest(AssignmentScopeType.CLASS, classId, null)), List.of(examiner1));

        var result = service.preview(sessionId, request, host);

        assertThat(result.valid()).isTrue();
        assertThat(result.supplemental()).isTrue();
        assertThat(result.attemptCount()).isEqualTo(1);
        ArgumentCaptor<ExaminerAssignmentBatch> batchCaptor = ArgumentCaptor.forClass(ExaminerAssignmentBatch.class);
        verify(batchRepository).saveAndFlush(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getAssignmentSnapshotJson()).contains(attempt2.toString())
                .doesNotContain(attempt1.toString());
    }

    @Test
    void nonHostCannotCreateAssignmentPreview() {
        CurrentUser student = new CurrentUser(UUID.randomUUID(), tenantId, List.of("STUDENT"));
        var request = new CreateExaminerAssignmentPreviewRequest(AssignmentBatchMode.RANDOM,
                List.of(new AssignmentScopeRequest(AssignmentScopeType.CLASS, classId, null)), List.of(examiner1));

        assertThatThrownBy(() -> service.preview(sessionId, request, student))
                .hasMessage("A tenant HOST_ADMIN is required.");
        verify(sessionService, never()).lockForExaminerAssignment(any(), any());
    }

    private void setCandidatePool(List<UUID> students, List<AttemptSummaryView> attempts) {
        when(enrollmentService.findActiveStudentPublicIds(tenantId, classId)).thenReturn(students);
        when(attemptService.getSubmittedAttemptsForSession(sessionId, tenantId)).thenReturn(attempts);
        when(eligibilityService.findEligibleAttempts(sessionId, tenantId,
                attempts.stream().map(AttemptSummaryView::attemptPublicId).toList()))
                .thenReturn(attempts.stream().map(attempt -> new AiEligibleAttemptView(
                        attempt.attemptPublicId(), 1)).toList());
    }

    private ExaminerIdentityView examiner(UUID id) {
        return new ExaminerIdentityView(id, "Examiner", "examiner@example.test");
    }

    private void stubBatchSave() {
        when(batchRepository.saveAndFlush(any(ExaminerAssignmentBatch.class))).thenAnswer(invocation -> {
            ExaminerAssignmentBatch batch = invocation.getArgument(0);
            batch.setPublicId(batchId);
            return batch;
        });
    }

    private List<UUID> ids(int count) {
        return IntStream.range(0, count).mapToObj(ignored -> UUID.randomUUID()).toList();
    }
}
