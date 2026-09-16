package com.pte.scoring;

import com.pte.scoring.dto.response.ScoredAnswerView;
import com.pte.scoring.internal.service.ScoredAnswerQueryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * The only door other modules use to reach {@code scoring}. Every internal
 * service, repository, and controller stays in {@code internal/}.
 *
 * <p>Starts with the one method known to have a cross-module caller: {@code
 * reporting} (Phase 10) aggregating an attempt's scored answers into its
 * skill report. No caller existed in Phase 08 itself, so this facade wasn't
 * created until now — same YAGNI-scoped pattern as every other module's
 * public facade.
 */
@Service
public class ScoringService {

    private final ScoredAnswerQueryService scoredAnswerQueryService;

    public ScoringService(ScoredAnswerQueryService scoredAnswerQueryService) {
        this.scoredAnswerQueryService = scoredAnswerQueryService;
    }

    public List<ScoredAnswerView> getScoredAnswersForAttempt(UUID attemptPublicId, UUID tenantId) {
        return scoredAnswerQueryService.findScoredForAttempt(attemptPublicId, tenantId);
    }
}
