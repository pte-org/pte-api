package com.pte.scoring;

import com.pte.scoring.domain.enums.GradingCohortStatus;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.internal.repository.GradingCohortRepository;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only grading backlog for dashboards. Grouped counts only (a tenant can hold
 * a very large number of answer rows), every query tenant-scoped and soft-delete
 * filtered, and only this module's own repositories are used, so the facade adds no
 * cross-module dependency to {@code scoring}.
 */
@Service
public class ScoringOverviewService {

    private final ScoringAnswerRepository answerRepository;
    private final GradingCohortRepository cohortRepository;

    public ScoringOverviewService(ScoringAnswerRepository answerRepository,
            GradingCohortRepository cohortRepository) {
        this.answerRepository = answerRepository;
        this.cohortRepository = cohortRepository;
    }

    /**
     * Answer counts by scoring state plus the number of closed sessions whose grading cohort is
     * frozen but not yet completed (grading started, finalisation still outstanding). Public and
     * plain so it can be cached as JSON.
     */
    public record ScoringBacklog(long pendingAnswers, long aiScoringAnswers, long failedAnswers,
            long closedSessionsAwaitingFinalisation) {
    }

    @Transactional(readOnly = true)
    public ScoringBacklog backlog(UUID tenantId) {
        Map<ScoringAnswerStatus, Long> byStatus = new EnumMap<>(ScoringAnswerStatus.class);
        for (Object[] row : answerRepository.countByTenantGroupedByStatus(tenantId)) {
            byStatus.put((ScoringAnswerStatus) row[0], ((Number) row[1]).longValue());
        }
        return new ScoringBacklog(
                byStatus.getOrDefault(ScoringAnswerStatus.PENDING, 0L),
                byStatus.getOrDefault(ScoringAnswerStatus.AI_SCORING, 0L),
                byStatus.getOrDefault(ScoringAnswerStatus.SCORING_FAILED, 0L),
                cohortRepository.countByTenantIdAndStatusAndDeletedFalse(tenantId, GradingCohortStatus.FROZEN));
    }
}
