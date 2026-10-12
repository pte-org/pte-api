package com.pte.scoring.internal.service;

import com.pte.attempt.AttemptStaffContextService;
import com.pte.attempt.dto.response.AttemptStaffContextView;
import com.pte.scoring.internal.repository.StaffScoreRow;
import com.pte.scoring.dto.response.ExaminerProgressView;
import com.pte.scoring.internal.repository.ExaminerAttemptAssignmentRepository;
import com.pte.scoring.internal.repository.ExaminerAnswerScoreRepository;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.scoring.internal.repository.ScoringSessionStateRepository;
import com.pte.shared.exception.DomainException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/** Streams bounded assignment and answer batches; current and published work share one predicate. */
@Service
public class StaffScoringQueryService {
    private static final int BATCH_SIZE = 100;
    private final ExaminerAttemptAssignmentRepository assignments;
    private final ScoringAnswerRepository answers;
    private final ExaminerAnswerScoreRepository scores;
    private final ScoringSessionStateRepository states;
    private final ScoringEligibilityQueryService eligibility;
    private final AttemptStaffContextService canonicalAttempts;

    public StaffScoringQueryService(ExaminerAttemptAssignmentRepository assignments, ScoringAnswerRepository answers,
            ExaminerAnswerScoreRepository scores, ScoringSessionStateRepository states,
            ScoringEligibilityQueryService eligibility, AttemptStaffContextService canonicalAttempts) {
        this.assignments = assignments;
        this.answers = answers;
        this.scores = scores;
        this.states = states;
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
                    PageRequest.of(page++, BATCH_SIZE, Sort.by("batchPublicId", "id")));
            var attempts = new LinkedHashMap<UUID, StaffAttemptProgress>();
            var contexts = canonicalAttempts.find(tenantId,
                    batch.getContent().stream().map(row -> row.getAttemptPublicId()).toList());
            for (var assignment : batch) {
                var context = contexts.get(assignment.getAttemptPublicId());
                if (context == null || !context.sessionPublicId().equals(assignment.getSessionPublicId())) { continue; }
                var progress = new StaffAttemptProgress(assignment, context.scoreTemplatePublicId());
                validatePinnedContext(progress, context);
                attempts.put(assignment.getAttemptPublicId(), progress);
            }
            if (!attempts.isEmpty()) {
                readAnswers(tenantId, attempts);
                var counts = scores.countStaffScores(tenantId, sessionIds, examinerId, List.copyOf(attempts.keySet()))
                        .stream().collect(Collectors.toMap(ExaminerAnswerScoreRepository.StaffScoreCount::getAttemptPublicId,
                                ExaminerAnswerScoreRepository.StaffScoreCount::getScoreCount));
                attempts.forEach((id, attempt) -> totals.computeIfAbsent(attempt.assignment.getSessionPublicId(),
                        StaffSessionProgress::new).accept(attempt, counts.getOrDefault(id, 0L)));
            }
            more = batch.hasNext();
        } while (more);
        var published = states.findByTenantIdAndSessionPublicIdInAndDeletedFalse(tenantId, sessionIds).stream()
                .filter(state -> state.getPublicationPublicId() != null).map(state -> state.getSessionPublicId())
                .collect(Collectors.toSet());
        var result = new LinkedHashMap<UUID, ExaminerProgressView>();
        totals.forEach((id, total) -> result.put(id, total.view(published.contains(id))));
        return result;
    }

    private void validatePinnedContext(StaffAttemptProgress progress, AttemptStaffContextView context) {
        if (!context.pinnedContextValid()) {
            progress.diagnostics.add(StaffAttemptProgress.TEMPLATE_UNAVAILABLE);
            return;
        }
        try {
            for (var task : context.taskCounts().entrySet()) {
                if (eligibility.resolvePinnedEligibility(context.scoreTemplatePublicId(), task.getKey()).orElse(false)) {
                    progress.expectedEligible += task.getValue();
                }
            }
        } catch (DomainException exception) {
            progress.diagnostics.add(StaffAttemptProgress.TEMPLATE_UNAVAILABLE);
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
                    : scores.findByTenantIdAndAnswerPublicIdInAndDeletedFalse(tenantId, answerIds).stream()
                            .collect(Collectors.groupingBy(StaffScoreRow::getAnswerPublicId));
            for (var answer : batch) {
                var attempt = attempts.get(answer.getAttemptPublicId());
                if (answer.getScoreTemplatePublicId() == null || answer.getTaskType() == null) {
                    attempt.diagnostics.add(StaffAttemptProgress.TEMPLATE_UNAVAILABLE);
                    continue;
                }
                try {
                    // Missing task row on an otherwise valid pinned template is legitimately unscored.
                    boolean eligibleAnswer = eligibility.resolvePinnedEligibility(
                            answer.getScoreTemplatePublicId(), answer.getTaskType()).orElse(false);
                    attempt.accept(answer, eligibleAnswer, scoreRows.getOrDefault(answer.getAnswerPublicId(), List.of()));
                } catch (DomainException exception) {
                    attempt.diagnostics.add(StaffAttemptProgress.TEMPLATE_UNAVAILABLE);
                }
            }
            more = batch.hasNext();
        } while (more);
    }
}
