package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.internal.generation.domain.BatchStatus;

/** The final status of a batch, recomputed from its item counters (never counted up). Pure. */
public final class BatchStatusCalculator {

    private BatchStatusCalculator() {
    }

    public static BatchStatus of(boolean cancelRequested, int succeeded, int total) {
        if (cancelRequested) {
            return BatchStatus.CANCELLED;
        }
        if (succeeded >= total) {
            return BatchStatus.COMPLETED;
        }
        return succeeded > 0 ? BatchStatus.PARTIALLY_COMPLETED : BatchStatus.FAILED;
    }
}
