package com.pte.attempt.internal.service;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Applies a proctor's force-submit command — backs the public {@code
 * com.pte.attempt.AttemptService#forceSubmit} that proctoring (Phase 09)
 * will call directly, in-process, with no repository access of its own. The
 * actor here is a verified proctor command already tenant-scoped by the
 * caller, not the student themselves — no ownership check, only a tenant
 * check. A silent no-op if the attempt doesn't exist or isn't {@code
 * IN_PROGRESS} (honest completion — a stale/duplicate/late command is not an
 * error).
 */
@Service
public class ProctorCommandService {

    private final ExamAttemptRepository attemptRepository;

    public ProctorCommandService(ExamAttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    @Transactional
    public void forceSubmit(UUID attemptPublicId, UUID tenantId) {
        inProgressAttempt(attemptPublicId, tenantId).ifPresent(attempt -> {
            attempt.submit();
            attemptRepository.save(attempt);
        });
    }

    private Optional<ExamAttempt> inProgressAttempt(UUID attemptPublicId, UUID tenantId) {
        return attemptRepository.findByPublicIdAndTenantId(attemptPublicId, tenantId)
                .filter(attempt -> attempt.getStatus() == AttemptStatus.IN_PROGRESS);
    }
}
