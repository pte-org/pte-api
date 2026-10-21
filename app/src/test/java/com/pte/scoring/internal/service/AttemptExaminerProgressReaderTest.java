package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ExaminerAttemptAssignment;
import com.pte.scoring.domain.ExaminerGradingWork;
import com.pte.scoring.domain.enums.ExaminerWorkflowStatus;
import com.pte.scoring.internal.dto.response.AttemptExaminerProgress;
import com.pte.scoring.internal.repository.AttemptScoreCount;
import com.pte.scoring.internal.repository.ExaminerAnswerDraftRepository;
import com.pte.scoring.internal.repository.ExaminerAnswerScoreRepository;
import com.pte.scoring.internal.repository.ExaminerAttemptAssignmentRepository;
import com.pte.scoring.internal.repository.ExaminerGradingWorkRepository;
import com.pte.scoring.internal.repository.WorkDraftCount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttemptExaminerProgressReaderTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID examinerId = UUID.randomUUID();

    @Mock
    private ExaminerAttemptAssignmentRepository assignmentRepository;
    @Mock
    private ExaminerGradingWorkRepository workRepository;
    @Mock
    private ExaminerAnswerDraftRepository draftRepository;
    @Mock
    private ExaminerAnswerScoreRepository scoreRepository;
    @InjectMocks
    private AttemptExaminerProgressReader reader;

    @Test
    void read_noAssignments_returnsEmptyWithoutFurtherQueries() {
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of());

        assertThat(reader.read(tenantId, sessionId)).isEmpty();
        verifyNoInteractions(workRepository, draftRepository, scoreRepository);
    }

    @Test
    void read_startedWork_usesWorkStatusAndSavedDraftCount() {
        UUID attempt = UUID.randomUUID();
        ExaminerAttemptAssignment assignment = assignment(attempt, 20);
        ExaminerGradingWork work = work(7L, assignment, ExaminerWorkflowStatus.IN_PROGRESS);
        List<ExaminerAttemptAssignment> assignments = List.of(assignment);
        List<ExaminerGradingWork> works = List.of(work);
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(assignments);
        when(workRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(works);
        when(draftRepository.countByWork(tenantId, List.of(7L))).thenReturn(List.of(new WorkDraftCount(7L, 12)));
        when(scoreRepository.countBySessionAndAttempt(tenantId, sessionId)).thenReturn(List.of());

        AttemptExaminerProgress progress = reader.read(tenantId, sessionId).get(attempt);

        assertThat(progress.examinerPublicId()).isEqualTo(examinerId);
        assertThat(progress.status()).isEqualTo(ExaminerWorkflowStatus.IN_PROGRESS);
        assertThat(progress.eligibleCount()).isEqualTo(20);
        assertThat(progress.savedCount()).isEqualTo(12);
    }

    @Test
    void read_assignmentNeverStarted_isPendingWithNothingSaved() {
        UUID attempt = UUID.randomUUID();
        List<ExaminerAttemptAssignment> assignments = List.of(assignment(attempt, 20));
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(assignments);
        when(workRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of());
        when(scoreRepository.countBySessionAndAttempt(tenantId, sessionId)).thenReturn(List.of());

        AttemptExaminerProgress progress = reader.read(tenantId, sessionId).get(attempt);

        assertThat(progress.status()).isEqualTo(ExaminerWorkflowStatus.PENDING);
        assertThat(progress.savedCount()).isZero();
        verifyNoInteractions(draftRepository);
    }

    @Test
    void read_scoresWithoutWork_derivesLegacyStatusFromSubmittedScores() {
        UUID partial = UUID.randomUUID();
        UUID complete = UUID.randomUUID();
        List<ExaminerAttemptAssignment> assignments = List.of(assignment(partial, 4), assignment(complete, 4));
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(assignments);
        when(workRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of());
        when(scoreRepository.countBySessionAndAttempt(tenantId, sessionId))
                .thenReturn(List.of(new AttemptScoreCount(partial, 1), new AttemptScoreCount(complete, 4)));

        Map<UUID, AttemptExaminerProgress> progress = reader.read(tenantId, sessionId);

        assertThat(progress.get(partial).status()).isEqualTo(ExaminerWorkflowStatus.IN_PROGRESS);
        assertThat(progress.get(partial).savedCount()).isEqualTo(1);
        assertThat(progress.get(complete).status()).isEqualTo(ExaminerWorkflowStatus.COMPLETED);
        assertThat(progress.get(complete).savedCount()).isEqualTo(4);
    }

    private ExaminerAttemptAssignment assignment(UUID attempt, int eligible) {
        ExaminerAttemptAssignment assignment = mock(ExaminerAttemptAssignment.class);
        UUID publicId = UUID.randomUUID();
        when(assignment.getPublicId()).thenReturn(publicId);
        when(assignment.getAttemptPublicId()).thenReturn(attempt);
        when(assignment.getExaminerPublicId()).thenReturn(examinerId);
        when(assignment.getEligibleAnswerCount()).thenReturn(eligible);
        return assignment;
    }

    private ExaminerGradingWork work(long id, ExaminerAttemptAssignment assignment,
                                     ExaminerWorkflowStatus status) {
        ExaminerGradingWork work = mock(ExaminerGradingWork.class);
        UUID assignmentPublicId = assignment.getPublicId();
        when(work.getId()).thenReturn(id);
        when(work.getAssignmentPublicId()).thenReturn(assignmentPublicId);
        when(work.getStatus()).thenReturn(status);
        return work;
    }
}
