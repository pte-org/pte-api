package com.pte.attempt;

import com.pte.attempt.internal.repository.ExamAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read-only attempt counts for dashboards. Counts only (no attempt rows leave
 * the module), tenant-scoped, soft-deleted rows ignored. Time ranges are
 * half-open {@code [from, to)} in UTC instants; calendar bucketing is the
 * caller's concern.
 */
@Service
public class AttemptOverviewService {

    private final ExamAttemptRepository attemptRepository;

    public AttemptOverviewService(ExamAttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    /** Attempts submitted in {@code [from, to)}. */
    @Transactional(readOnly = true)
    public long countSubmitted(UUID tenantId, Instant from, Instant to) {
        return attemptRepository.countSubmittedBetween(tenantId, from, to);
    }

    /** Attempts that are in progress right now. */
    @Transactional(readOnly = true)
    public long countInProgress(UUID tenantId) {
        return attemptRepository.countInProgress(tenantId);
    }

    /** Distinct students who started at least one attempt in {@code [from, to)}. */
    @Transactional(readOnly = true)
    public long countDistinctActiveStudents(UUID tenantId, Instant from, Instant to) {
        return attemptRepository.countDistinctStudentsStartedBetween(tenantId, from, to);
    }

    /**
     * Platform-wide: distinct students (across every tenant) who started at least one attempt in
     * {@code [from, to)}. Not tenant-scoped, so only platform dashboards may call it.
     */
    @Transactional(readOnly = true)
    public long countDistinctActiveStudentsPlatformWide(Instant from, Instant to) {
        return attemptRepository.countDistinctStudentsStartedBetweenPlatformWide(from, to);
    }

    /**
     * Submitted attempts per consecutive range. {@code boundaries} holds n+1
     * ascending instants and the result holds n counts, one per
     * {@code [boundaries[i], boundaries[i+1])}. Each range is one indexed count.
     */
    @Transactional(readOnly = true)
    public List<Long> countSubmittedPerRange(UUID tenantId, List<Instant> boundaries) {
        if (boundaries == null || boundaries.size() < 2) {
            return List.of();
        }
        List<Long> counts = new ArrayList<>(boundaries.size() - 1);
        for (int i = 0; i < boundaries.size() - 1; i++) {
            counts.add(attemptRepository.countSubmittedBetween(tenantId, boundaries.get(i), boundaries.get(i + 1)));
        }
        return counts;
    }
}
