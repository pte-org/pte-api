package com.pte.examdelivery.domain.exception;

import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Defensive guard: pinning produced zero items. Should never happen since
 * scheduling validates composition non-empty at set-time — indicates an
 * upstream data inconsistency, not a normal user error.
 */
public class PinnedSnapshotEmptyException extends DomainException {

    public PinnedSnapshotEmptyException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "PINNED_SNAPSHOT_EMPTY");
    }
}
