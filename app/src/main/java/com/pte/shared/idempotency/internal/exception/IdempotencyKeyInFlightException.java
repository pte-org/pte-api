package com.pte.shared.idempotency.internal.exception;

import com.pte.shared.exception.DomainException;
import com.pte.shared.idempotency.internal.constant.IdempotencyConstants;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a concurrent request already claimed the same idempotency key. The losing request's
 * transaction is doomed by the unique-constraint violation, so it must roll back; the client retries
 * and then receives the winner's receipt.
 */
public class IdempotencyKeyInFlightException extends DomainException {

    public IdempotencyKeyInFlightException() {
        super(HttpStatus.CONFLICT, IdempotencyConstants.IDEMPOTENCY_KEY_IN_FLIGHT);
    }
}
