package com.pte.attempt.internal.service.query;

import com.pte.attempt.internal.service.ExamContentService;
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
    private final ExamContentService contentService;

    public AttemptGradingQueryService(ExamAttemptRepository attemptRepository, ExamContentService contentService) {
        this.attemptRepository = attemptRepository;
        this.contentService = contentService;
    }

    @Transactional(readOnly = true)
    public List<AttemptGradingCandidateView> findCandidates(UUID sessionPublicId, UUID tenantId) {
        return attemptRepository.findBySessionPublicIdAndTenantIdAndDeletedFalse(sessionPublicId, tenantId).stream()
                .map(attempt -> new AttemptGradingCandidateView(attempt.getPublicId(), attempt.getStudentPublicId(),
                        attempt.getStatus().name(), versionToken(attempt), attempt.isGradingExcluded(),
                        attempt.getGradingExclusionReason()))
                .toList();
    }

    @Transactional(readOnly = true)
    public java.util.Optional<AttemptGradingCandidateView> findCandidate(UUID attemptPublicId, UUID tenantId) {
        return attemptRepository.findByPublicIdAndTenantId(attemptPublicId, tenantId).filter(attempt -> !attempt.isDeleted())
                .map(attempt -> new AttemptGradingCandidateView(attempt.getPublicId(), attempt.getStudentPublicId(),
                        attempt.getStatus().name(), versionToken(attempt), attempt.isGradingExcluded(),
                        attempt.getGradingExclusionReason()));
    }

    /** Leaves the given attempts out of the session grading, each with the host's (or the automatic) reason. */
    @Transactional
    public void excludeFromGrading(UUID sessionPublicId, UUID tenantId, Map<UUID, String> reasonByAttempt) {
        reasonByAttempt.forEach((attemptPublicId, reason) ->
                attemptRepository.markGradingExcluded(attemptPublicId, tenantId, sessionPublicId, reason));
    }

    @Transactional(readOnly = true)
    public Map<UUID, AttemptGradingCoverageView> findCoverage(Collection<UUID> attemptPublicIds,
            UUID sessionPublicId, UUID tenantId) {
        if (attemptPublicIds == null || attemptPublicIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = attemptPublicIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        Map<UUID, AttemptGradingCoverageView> result = new LinkedHashMap<>();
        for (ExamAttempt attempt : attemptRepository.findAllByPublicIdInAndDeletedFalse(ids)) {
            if (!tenantId.equals(attempt.getTenantId()) || !sessionPublicId.equals(attempt.getSessionPublicId())) {
                continue;
            }
            List<AttemptGradingItemView> items = contentService.outline(attempt.getExamVersionPublicId()).stream()
                    .map(item -> new AttemptGradingItemView(item.publicId(), attempt.getExamDefinitionPublicId(),
                            item.taskType()))
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
