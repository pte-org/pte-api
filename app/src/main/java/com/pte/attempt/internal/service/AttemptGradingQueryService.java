package com.pte.attempt.internal.service;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.dto.response.AttemptGradingCandidateView;
import com.pte.attempt.dto.response.AttemptGradingCoverageView;
import com.pte.attempt.dto.response.AttemptGradingItemView;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Attempt-owned read surface for post-CLOSED grading coverage; never exposes attempt repositories. */
@Service
public class AttemptGradingQueryService {

    private final ExamAttemptRepository attemptRepository;

    public AttemptGradingQueryService(ExamAttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    @Transactional(readOnly = true)
    public List<AttemptGradingCandidateView> findCandidates(UUID sessionPublicId, UUID tenantId) {
        return attemptRepository.findBySessionPublicIdAndTenantIdAndDeletedFalse(sessionPublicId, tenantId).stream()
                .map(attempt -> new AttemptGradingCandidateView(attempt.getPublicId(), attempt.getStudentPublicId(),
                        attempt.getStatus().name(), versionToken(attempt)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<UUID, AttemptGradingCoverageView> findCoverage(Collection<UUID> attemptPublicIds,
            UUID sessionPublicId, UUID tenantId) {
        if (attemptPublicIds == null || attemptPublicIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = attemptPublicIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        Map<UUID, AttemptGradingCoverageView> result = new LinkedHashMap<>();
        for (ExamAttempt attempt : attemptRepository.findAllWithPinnedByPublicIds(ids)) {
            if (!tenantId.equals(attempt.getTenantId()) || !sessionPublicId.equals(attempt.getSessionPublicId())) {
                continue;
            }
            List<AttemptGradingItemView> items = attempt.getPinnedSnapshot() == null
                    ? List.of()
                    : attempt.getPinnedSnapshot().getItems().stream()
                            .map(item -> new AttemptGradingItemView(item.getPublicId(),
                                    attempt.getPinnedSnapshot().getScoreTemplatePublicId(), item.getTaskType()))
                            .toList();
            result.put(attempt.getPublicId(), new AttemptGradingCoverageView(attempt.getPublicId(), items));
        }
        return Map.copyOf(result);
    }

    private static String versionToken(ExamAttempt attempt) {
        return (attempt.getVersion() == null ? "0" : attempt.getVersion().toString()) + ":"
                + (attempt.getUpdatedAt() == null ? "" : attempt.getUpdatedAt().toString()) + ":"
                + attempt.getStatus().name();
    }
}
