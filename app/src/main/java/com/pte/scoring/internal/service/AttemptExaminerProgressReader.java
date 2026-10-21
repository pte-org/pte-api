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
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * How far each assigned examiner is through one session's attempts, in a fixed number of queries.
 * Mirrors the per-attempt status the examiner sees: a started or finalized grading work wins; an assignment
 * without one is pending, or in progress / completed when scores were submitted before the draft workflow.
 */
@Component
class AttemptExaminerProgressReader {

    private final ExaminerAttemptAssignmentRepository assignmentRepository;
    private final ExaminerGradingWorkRepository workRepository;
    private final ExaminerAnswerDraftRepository draftRepository;
    private final ExaminerAnswerScoreRepository scoreRepository;

    AttemptExaminerProgressReader(ExaminerAttemptAssignmentRepository assignmentRepository,
                                  ExaminerGradingWorkRepository workRepository,
                                  ExaminerAnswerDraftRepository draftRepository,
                                  ExaminerAnswerScoreRepository scoreRepository) {
        this.assignmentRepository = assignmentRepository;
        this.workRepository = workRepository;
        this.draftRepository = draftRepository;
        this.scoreRepository = scoreRepository;
    }

    /** Progress by attempt; attempts without an examiner assignment are absent. */
    Map<UUID, AttemptExaminerProgress> read(UUID tenantId, UUID sessionPublicId) {
        List<ExaminerAttemptAssignment> assignments = assignmentRepository
                .findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionPublicId);
        if (assignments.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ExaminerGradingWork> worksByAssignment = workRepository
                .findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionPublicId).stream()
                .collect(Collectors.toMap(ExaminerGradingWork::getAssignmentPublicId, Function.identity(),
                        (first, second) -> first));
        Map<Long, Long> draftsByWork = worksByAssignment.isEmpty() ? Map.of()
                : draftRepository.countByWork(tenantId,
                                worksByAssignment.values().stream().map(ExaminerGradingWork::getId).toList())
                        .stream().collect(Collectors.toMap(WorkDraftCount::workId, WorkDraftCount::count));
        Map<UUID, Long> scoresByAttempt = scoreRepository.countBySessionAndAttempt(tenantId, sessionPublicId)
                .stream().collect(Collectors.toMap(AttemptScoreCount::attemptPublicId, AttemptScoreCount::count));

        Map<UUID, AttemptExaminerProgress> progress = new HashMap<>();
        for (ExaminerAttemptAssignment assignment : assignments) {
            ExaminerGradingWork work = worksByAssignment.get(assignment.getPublicId());
            int eligible = assignment.getEligibleAnswerCount();
            ExaminerWorkflowStatus status;
            int saved;
            if (work != null) {
                status = work.getStatus();
                saved = draftsByWork.getOrDefault(work.getId(), 0L).intValue();
            } else {
                saved = scoresByAttempt.getOrDefault(assignment.getAttemptPublicId(), 0L).intValue();
                status = saved == 0 ? ExaminerWorkflowStatus.PENDING
                        : saved < eligible ? ExaminerWorkflowStatus.IN_PROGRESS : ExaminerWorkflowStatus.COMPLETED;
            }
            progress.put(assignment.getAttemptPublicId(),
                    new AttemptExaminerProgress(assignment.getExaminerPublicId(), status, eligible, saved));
        }
        return progress;
    }
}
