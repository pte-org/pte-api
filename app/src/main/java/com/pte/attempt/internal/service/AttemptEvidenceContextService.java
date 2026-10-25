package com.pte.attempt.internal.service;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.dto.response.AttemptEvidenceContextView;
import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.attempt.internal.exception.AttemptNotFoundException;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/** Backs proctoring recording student lockdown evidence: ownership check plus the attempt row lock. */
@Service
public class AttemptEvidenceContextService {

    private final ExamAttemptRepository attemptRepository;

    public AttemptEvidenceContextService(ExamAttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    /** The row lock serializes two concurrent first deliveries of the same client event for one attempt. */
    @Transactional
    public AttemptEvidenceContextView lockOwn(UUID attemptPublicId, CurrentUser caller) {
        if (caller == null || caller.userId() == null || caller.tenantId() == null) {
            throw new AttemptNotFoundException();
        }
        ExamAttempt attempt = attemptRepository.findByPublicIdAndStudentPublicId(attemptPublicId, caller.userId())
                .filter(candidate -> Objects.equals(candidate.getTenantId(), caller.tenantId()))
                .orElseThrow(AttemptNotFoundException::new);
        ExamAttempt locked = attemptRepository.findWithLockById(attempt.getId())
                .orElseThrow(() -> new IllegalStateException(
                        String.format(AttemptConstants.ATTEMPT_DISAPPEARED_MID_TRANSACTION, attempt.getId())));
        return new AttemptEvidenceContextView(locked.getPublicId(), locked.getSessionPublicId(),
                locked.getStudentPublicId(), locked.getTenantId(), locked.getLockdownMode());
    }
}
