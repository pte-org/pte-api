package com.pte.scheduling.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.scheduling.constant.SchedulingConstants;
import org.springframework.http.HttpStatus;

/**
 * The guarded sync pull to authoring failed (timeout, circuit open, 404).
 * Distinct from {@link SessionNotFoundException} — this means the snapshot
 * itself is unreachable/unknown, not that the session record is missing.
 */
public class SnapshotFetchFailedException extends DomainException {

    public SnapshotFetchFailedException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, SchedulingConstants.SNAPSHOT_FETCH_FAILED);
    }
}
