package com.pte.attempt.internal.service;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.attempt.internal.exception.AttemptNotFoundException;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;

import java.time.Clock;
import java.util.UUID;

/**
 * Ownership lookup, session/attempt row locking and the single completion
 * choke point shared by every attempt lifecycle operation.
 */
class AttemptAccess {

    private final ExamAttemptRepository attemptRepository;
    private final SessionService sessionService;
    private final Clock clock;

    AttemptAccess(ExamAttemptRepository attemptRepository, SessionService sessionService, Clock clock) {
        this.attemptRepository = attemptRepository;
        this.sessionService = sessionService;
        this.clock = clock;
    }

    ExamAttempt findOwned(UUID publicId, UUID studentPublicId) {
        return attemptRepository.findByPublicIdAndStudentPublicId(publicId, studentPublicId)
                .orElseThrow(AttemptNotFoundException::new);
    }

    ExamAttempt findOwned(UUID publicId, CurrentUser caller) {
        ExamAttempt attempt = findOwned(publicId, caller.userId());
        requireTenantOwnership(attempt, caller);
        return attempt;
    }

    void lockOpenSession(ExamAttempt attempt) {
        sessionService.lockOpenForAttemptOperation(attempt.getSessionPublicId(), attempt.getTenantId());
    }

    void requireTenantOwnership(ExamAttempt attempt, CurrentUser caller) {
        if (caller.tenantId() == null || attempt.getTenantId() == null
                || !caller.tenantId().equals(attempt.getTenantId())) {
            throw new AttemptNotFoundException();
        }
    }

    /**
     * Re-fetches {@code attempt} under a pessimistic write lock, by id, within the
     * same persistence context — returns the SAME managed instance (JPA identity map),
     * so already-loaded state stays populated.
     * Every mutator of the relocated {@code currentOrderIndex}/{@code playCount}/
     * {@code lastPlayRequestId}/{@code lastPlayAllowed} fields must call this first.
     */
    ExamAttempt lockAttempt(ExamAttempt attempt) {
        Long attemptId = attempt.getId();
        return attemptRepository.findWithLockById(attemptId)
                .orElseThrow(() -> new IllegalStateException(
                        String.format(AttemptConstants.ATTEMPT_DISAPPEARED_MID_TRANSACTION, attemptId)));
    }

    /**
     * Locked here (not just at each caller) so every path to completing an
     * attempt — including submitAttempt, which had no other lock on this
     * @Version-guarded row — is covered by a single choke point. Callers that
     * already locked earlier in the same transaction just re-acquire their
     * own already-held lock — a cheap, harmless no-op, not a double-lock
     * hazard (same transaction, same row).
     *
     * <p>{@link ExamAttempt#submit()} registers an {@code AttemptSubmittedEvent}, which
     * the repository publishes on save; scoring ingests the answers after commit.
     */
    void completeAttempt(ExamAttempt attempt) {
        lockAttempt(attempt);
        attempt.submit(clock.instant());
        attemptRepository.save(attempt);
    }
}
