package com.pte.scoring.internal.service.staff;

import com.pte.scoring.internal.service.grading.SessionGradingGateway;
import com.pte.scoring.internal.service.objective.ScoringMethods;
import com.pte.attempt.AttemptStaffContextService;
import com.pte.attempt.dto.response.AttemptStaffContextView;
import com.pte.scoring.internal.repository.StaffScoreRow;
import com.pte.scoring.dto.response.ExaminerProgressView;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.scoring.internal.repository.ExaminerEvaluationRepository;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/** Streams bounded assignment and answer batches; current and published work share one predicate. */
@Service
public class StaffScoringQueryService {
    private static final int BATCH_SIZE = 100;
    private final GradingAssignmentRepository assignments;
    private final AiEvaluationRepository answers;
    private final ExaminerEvaluationRepository scores;
    private final SessionGradingGateway gateway;
    private final ScoringEligibilityQueryService eligibility;
    private final AttemptStaffContextService canonicalAttempts;

    public StaffScoringQueryService(GradingAssignmentRepository assignments, AiEvaluationRepository answers,
            ExaminerEvaluationRepository scores, SessionGradingGateway gateway,
            ScoringEligibilityQueryService eligibility, AttemptStaffContextService canonicalAttempts) {
        this.assignments = assignments;
        this.answers = answers;
        this.scores = scores;
        this.gateway = gateway;
        this.eligibility = eligibility;
        this.canonicalAttempts = canonicalAttempts;
    }

    public Map<UUID, ExaminerProgressView> summarize(UUID tenantId, UUID examinerId, List<UUID> sessionIds) {
        if (sessionIds.isEmpty()) { return Map.of(); }
        var totals = new LinkedHashMap<UUID, StaffSessionProgress>();
        int page = 0;
        boolean more;
        do {
            var batch = assignments.findByTenantIdAndExaminerPublicIdAndSessionPublicIdInAndDeletedFalse(
                    tenantId, examinerId, sessionIds,
                    PageRequest.of(page++, BATCH_SIZE, Sort.by("id")));
            var attempts = new LinkedHashMap<UUID, StaffAttemptProgress>();
            var contexts = canonicalAttempts.find(tenantId,
                    batch.getContent().stream().map(row -> row.getAttemptPublicId()).toList());
            for (var assignment : batch) {
                var context = contexts.get(assignment.getAttemptPublicId());
                if (context == null || !context.sessionPublicId().equals(assignment.getSessionPublicId())) { continue; }
                var progress = new StaffAttemptProgress(assignment, context.examDefinitionPublicId());
                validatePinnedContext(progress, context);
                attempts.put(assignment.getAttemptPublicId(), progress);
            }
            if (!attempts.isEmpty()) {
                readAnswers(tenantId, attempts);
                var counts = scores.countStaffScores(tenantId, sessionIds, examinerId, List.copyOf(attempts.keySet()))
                        .stream().collect(Collectors.toMap(ExaminerEvaluationRepository.StaffScoreCount::getAttemptPublicId,
                                ExaminerEvaluationRepository.StaffScoreCount::getScoreCount));
                attempts.forEach((id, attempt) -> totals.computeIfAbsent(attempt.assignment.getSessionPublicId(),
                        StaffSessionProgress::new).accept(attempt, counts.getOrDefault(id, 0L)));
            }
            more = batch.hasNext();
        } while (more);
        var published = gateway.publications(tenantId, sessionIds).keySet();
        var result = new LinkedHashMap<UUID, ExaminerProgressView>();
        totals.forEach((id, total) -> result.put(id, total.view(published.contains(id))));
        return result;
    }

    private void validatePinnedContext(StaffAttemptProgress progress, AttemptStaffContextView context) {
        if (!context.examContextValid()) {
            progress.diagnostics.add(StaffAttemptProgress.TEMPLATE_UNAVAILABLE);
            return;
        }
        for (var task : context.taskCounts().entrySet()) {
            if (ScoringMethods.isAiScored(context.runtimes().get(task.getKey()))) {
                progress.expectedEligible += task.getValue();
            }
        }
    }

    private void readAnswers(UUID tenantId, Map<UUID, StaffAttemptProgress> attempts) {
        int page = 0;
        boolean more;
        do {
            var batch = answers.findByTenantIdAndAttemptPublicIdInAndDeletedFalse(tenantId,
                    List.copyOf(attempts.keySet()), PageRequest.of(page++, BATCH_SIZE, Sort.by("id")));
            var answerIds = batch.getContent().stream().map(answer -> answer.getAnswerPublicId()).toList();
            Map<UUID, List<StaffScoreRow>> scoreRows = answerIds.isEmpty() ? Map.of()
                    : scores.findSubmittedRows(tenantId, answerIds).stream()
                            .collect(Collectors.groupingBy(StaffScoreRow::getAnswerPublicId));
            for (var answer : batch) {
                var attempt = attempts.get(answer.getAttemptPublicId());
                if (answer.getExamDefinitionPublicId() == null || answer.getTaskType() == null) {
                    attempt.diagnostics.add(StaffAttemptProgress.TEMPLATE_UNAVAILABLE);
                    continue;
                }
                // An item without a scoring rule is legitimately unscored, not an error.
                boolean eligibleAnswer = answer.getScoringMethod() != null && answer.getScoringMethod().isAiScored();
                attempt.accept(answer, eligibleAnswer, scoreRows.getOrDefault(answer.getAnswerPublicId(), List.of()));
            }
            more = batch.hasNext();
        } while (more);
    }
}
