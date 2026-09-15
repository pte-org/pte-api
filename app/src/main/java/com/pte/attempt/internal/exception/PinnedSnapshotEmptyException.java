package com.pte.attempt.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Defensive guard: pinning produced zero items. Should never happen since
 * session validates composition against assessment's snapshot at set-time —
 * indicates an upstream data inconsistency, not a normal user error.
 */
public class PinnedSnapshotEmptyException extends DomainException {

    public PinnedSnapshotEmptyException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "PINNED_SNAPSHOT_EMPTY");
    }
}
