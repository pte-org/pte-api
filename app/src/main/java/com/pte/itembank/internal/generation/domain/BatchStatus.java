package com.pte.itembank.internal.generation.domain;

import java.util.EnumSet;
import java.util.Set;

/** Lifecycle of a generation batch. The terminal status is recomputed from its children, never counted up. */
public enum BatchStatus {
    QUEUED, RUNNING, CANCELLING, COMPLETED, PARTIALLY_COMPLETED, FAILED, CANCELLED;

    /** Mirrors the one-open-batch-per-requester partial unique index. */
    public static final Set<BatchStatus> OPEN = EnumSet.of(QUEUED, RUNNING, CANCELLING);
    public static final Set<BatchStatus> TERMINAL = EnumSet.of(COMPLETED, PARTIALLY_COMPLETED, FAILED, CANCELLED);
}
