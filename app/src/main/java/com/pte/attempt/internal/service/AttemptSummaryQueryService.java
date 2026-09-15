package com.pte.attempt.internal.service;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.attempt.internal.exception.AttemptNotFoundException;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Backs reporting's report creation/publish pull (Phase 10) — reporting has no repository access of its own into attempt. */
@Service
public class AttemptSummaryQueryService {

    private final ExamAttemptRepository attemptRepository;

    public AttemptSummaryQueryService(ExamAttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    /** Throws if the attempt doesn't exist or hasn't reached SUBMITTED — no tenant filter, the trusted caller checks that itself against the returned tenantId. */
    @Transactional(readOnly = true)
    public AttemptSummaryView findSubmitted(UUID attemptPublicId) {
        ExamAttempt attempt = attemptRepository.findByPublicIdAndStatus(attemptPublicId, AttemptStatus.SUBMITTED)
                .orElseThrow(AttemptNotFoundException::new);
        return toView(attempt);
    }

    @Transactional(readOnly = true)
    public List<AttemptSummaryView> findSubmittedForSession(UUID sessionPublicId, UUID tenantId) {
        return attemptRepository.findBySessionPublicIdAndTenantIdAndStatus(sessionPublicId, tenantId, AttemptStatus.SUBMITTED)
                .stream().map(AttemptSummaryQueryService::toView).toList();
    }

    private static AttemptSummaryView toView(ExamAttempt attempt) {
        return new AttemptSummaryView(attempt.getPublicId(), attempt.getSessionPublicId(),
                attempt.getStudentPublicId(), attempt.getTenantId());
    }
}
