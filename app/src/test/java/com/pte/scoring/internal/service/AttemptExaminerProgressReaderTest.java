package com.pte.scoring.internal.service;

import com.pte.scoring.domain.GradingAssignment;
import com.pte.scoring.domain.enums.ExaminerWorkflowStatus;
import com.pte.scoring.internal.dto.response.AttemptExaminerProgress;
import com.pte.scoring.internal.repository.AttemptScoreCount;
import com.pte.scoring.internal.repository.ExaminerEvaluationRepository;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.scoring.internal.repository.DraftCount;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttemptExaminerProgressReaderTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID examinerId = UUID.randomUUID();

    @Mock
    private GradingAssignmentRepository assignmentRepository;
    @Mock
    private ExaminerEvaluationRepository scoreRepository;
    @InjectMocks
    private AttemptExaminerProgressReader reader;

    @Test
    void read_noAssignments_returnsEmptyWithoutFurtherQueries() {
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(List.of());

        assertThat(reader.read(tenantId, sessionId)).isEmpty();
        verifyNoInteractions(scoreRepository);
    }

    @Test
    void read_startedWork_usesWorkStatusAndSavedDraftCount() {
        UUID attempt = UUID.randomUUID();
        GradingAssignment assignment = assignment(attempt, 20);
        when(assignment.isStarted()).thenReturn(true);
        when(assignment.getId()).thenReturn(7L);
        when(assignment.getStatus()).thenReturn(ExaminerWorkflowStatus.IN_PROGRESS);
        List<GradingAssignment> assignments = List.of(assignment);
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(assignments);
        when(scoreRepository.countByAssignment(tenantId, List.of(7L))).thenReturn(List.of(new DraftCount(7L, 12L)));
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
        List<GradingAssignment> assignments = List.of(assignment(attempt, 20));
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(assignments);
        when(scoreRepository.countBySessionAndAttempt(tenantId, sessionId)).thenReturn(List.of());

        AttemptExaminerProgress progress = reader.read(tenantId, sessionId).get(attempt);

        assertThat(progress.status()).isEqualTo(ExaminerWorkflowStatus.PENDING);
        assertThat(progress.savedCount()).isZero();
        verify(scoreRepository, never()).countByAssignment(any(), any());
    }

    @Test
    void read_scoresOnUnstartedAssignment_derivesStatusFromSubmittedScores() {
        UUID partial = UUID.randomUUID();
        UUID complete = UUID.randomUUID();
        List<GradingAssignment> assignments = List.of(assignment(partial, 4), assignment(complete, 4));
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .thenReturn(assignments);
        when(scoreRepository.countBySessionAndAttempt(tenantId, sessionId))
                .thenReturn(List.of(new AttemptScoreCount(partial, 1), new AttemptScoreCount(complete, 4)));

        Map<UUID, AttemptExaminerProgress> progress = reader.read(tenantId, sessionId);

        assertThat(progress.get(partial).status()).isEqualTo(ExaminerWorkflowStatus.IN_PROGRESS);
        assertThat(progress.get(partial).savedCount()).isEqualTo(1);
        assertThat(progress.get(complete).status()).isEqualTo(ExaminerWorkflowStatus.COMPLETED);
        assertThat(progress.get(complete).savedCount()).isEqualTo(4);
    }

    private GradingAssignment assignment(UUID attempt, int eligible) {
        GradingAssignment assignment = mock(GradingAssignment.class, org.mockito.Mockito.withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
        UUID publicId = UUID.randomUUID();
        when(assignment.getPublicId()).thenReturn(publicId);
        when(assignment.getAttemptPublicId()).thenReturn(attempt);
        when(assignment.getExaminerPublicId()).thenReturn(examinerId);
        when(assignment.getEligibleAnswerCount()).thenReturn(eligible);
        return assignment;
    }
}
