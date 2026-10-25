package com.pte.scoring.internal.service;

import com.pte.scoring.domain.GradingAssignment;
import com.pte.scoring.domain.enums.ExaminerWorkflowStatus;
import com.pte.scoring.internal.dto.response.AttemptExaminerProgress;
import com.pte.scoring.internal.repository.AttemptScoreCount;
import com.pte.scoring.internal.repository.ExaminerEvaluationRepository;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.scoring.internal.repository.DraftCount;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * How far each assigned examiner is through one session's attempts, in a fixed number of queries.
 * Mirrors the per-attempt status the examiner sees: a started or finalized grading work wins; an assignment
 * without one is pending, or in progress / completed when scores were submitted before the draft workflow.
 */
@Component
class AttemptExaminerProgressReader {

    private final GradingAssignmentRepository assignmentRepository;
    private final ExaminerEvaluationRepository evaluationRepository;

    AttemptExaminerProgressReader(GradingAssignmentRepository assignmentRepository,
                                  ExaminerEvaluationRepository evaluationRepository) {
        this.assignmentRepository = assignmentRepository;
        this.evaluationRepository = evaluationRepository;
    }

    /** Progress by attempt; attempts without an examiner assignment are absent. */
    Map<UUID, AttemptExaminerProgress> read(UUID tenantId, UUID sessionPublicId) {
        List<GradingAssignment> assignments = assignmentRepository
                .findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionPublicId);
        if (assignments.isEmpty()) {
            return Map.of();
        }
        List<Long> startedIds = assignments.stream().filter(GradingAssignment::isStarted)
                .map(GradingAssignment::getId).toList();
        Map<Long, Long> draftsByAssignment = startedIds.isEmpty() ? Map.of()
                : evaluationRepository.countByAssignment(tenantId, startedIds)
                        .stream().collect(Collectors.toMap(DraftCount::assignmentId, DraftCount::count));
        Map<UUID, Long> scoresByAttempt = evaluationRepository.countBySessionAndAttempt(tenantId, sessionPublicId)
                .stream().collect(Collectors.toMap(AttemptScoreCount::attemptPublicId, AttemptScoreCount::count));

        Map<UUID, AttemptExaminerProgress> progress = new HashMap<>();
        for (GradingAssignment assignment : assignments) {
            int eligible = assignment.getEligibleAnswerCount();
            ExaminerWorkflowStatus status;
            int saved;
            if (assignment.isStarted()) {
                status = assignment.getStatus();
                saved = draftsByAssignment.getOrDefault(assignment.getId(), 0L).intValue();
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
