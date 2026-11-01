package com.pte.scoring.internal.service.staff;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptGradingCandidateView;
import com.pte.attempt.dto.response.AttemptSummaryView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Which submitted attempts of a session can be handed to an examiner, and how many answers each carries. */
public final class SubmittedAttemptReader {

    private final AttemptService attemptService;
    private final ScoringEligibilityQueryService eligibilityQueryService;

    public SubmittedAttemptReader(AttemptService attemptService, ScoringEligibilityQueryService eligibilityQueryService) {
        this.attemptService = attemptService;
        this.eligibilityQueryService = eligibilityQueryService;
    }

    public Map<UUID, AttemptSummaryView> submitted(UUID sessionPublicId, UUID tenantId) {
        return attemptService.getSubmittedAttemptsForSession(sessionPublicId, tenantId).stream()
                .collect(Collectors.toMap(AttemptSummaryView::attemptPublicId, attempt -> attempt,
                        (left, right) -> left, LinkedHashMap::new));
    }

    /** Submitted attempts that carry at least one AI-eligible answer and are not excluded from the grading. */
    public Map<UUID, Integer> eligibleCounts(UUID sessionPublicId, UUID tenantId, Map<UUID, AttemptSummaryView> submitted) {
        Set<UUID> excluded = attemptService.getGradingCandidatesForSession(sessionPublicId, tenantId).stream()
                .filter(AttemptGradingCandidateView::excluded)
                .map(AttemptGradingCandidateView::attemptPublicId).collect(Collectors.toSet());
        List<UUID> candidates = submitted.keySet().stream().filter(id -> !excluded.contains(id)).toList();
        return eligibilityQueryService.findEligibleAttempts(sessionPublicId, tenantId, candidates).stream()
                .collect(Collectors.toMap(view -> view.attemptPublicId(), view -> view.eligibleAnswerCount(),
                        (left, right) -> left, LinkedHashMap::new));
    }
}
