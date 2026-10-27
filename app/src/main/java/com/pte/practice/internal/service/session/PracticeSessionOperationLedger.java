package com.pte.practice.internal.service.session;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.enums.PracticeSessionOperationType;
import com.pte.practice.internal.exception.PracticeIdempotencyException;
import com.pte.shared.idempotency.IdempotencyDecision;
import com.pte.shared.idempotency.IdempotencyKeyService;
import com.pte.shared.idempotency.IdempotencyOutcome;
import com.pte.shared.idempotency.domain.IdempotencyScope;

import java.time.Clock;

/** Claims and completes the per-operation idempotency key of a session mutation. */
class PracticeSessionOperationLedger {

    private final IdempotencyKeyService idempotencyKeyService;
    private final Clock clock;

    PracticeSessionOperationLedger(IdempotencyKeyService idempotencyKeyService, Clock clock) {
        this.idempotencyKeyService = idempotencyKeyService;
        this.clock = clock;
    }

    /** Claims the operation key; true when the same request was already applied and its result can be replayed. */
    boolean alreadyApplied(PracticeSession session, PracticeSessionOperationType operationType,
            String idempotencyKey, String requestHash) {
        IdempotencyDecision decision = idempotencyKeyService.begin(IdempotencyScope.PRACTICE_SESSION_OP,
                operationKey(session, operationType, idempotencyKey), session.getTenantId(), requestHash,
                clock.instant());
        if (decision.outcome() == IdempotencyOutcome.CONFLICT) {
            throw PracticeIdempotencyException.reused();
        }
        return decision.outcome() == IdempotencyOutcome.REPLAY;
    }

    void recordOperation(PracticeSession session, PracticeSessionOperationType operationType,
            String idempotencyKey) {
        idempotencyKeyService.complete(IdempotencyScope.PRACTICE_SESSION_OP,
                operationKey(session, operationType, idempotencyKey), session.getTenantId(),
                PracticeConstants.OPERATION_RECEIPT);
    }

    /** The client key is hashed so the stored key stays within the shared column whatever length the client sent. */
    private String operationKey(PracticeSession session, PracticeSessionOperationType operationType,
            String idempotencyKey) {
        return PracticeConstants.OPERATION_KEY_FORMAT.formatted(session.getPublicId(), operationType.name(),
                PracticeSessionRequestHashes.sha256(idempotencyKey));
    }
}
