package com.pte.itembank.internal.generation.domain;

import java.util.EnumSet;
import java.util.Set;

/** Lifecycle of a generation batch. The terminal status is recomputed from its children, never counted up. */
public enum BatchStatus {
    QUEUED, RUNNING, CANCELLING, PAUSING, PAUSED, COMPLETED, PARTIALLY_COMPLETED, FAILED, CANCELLED;

    /**
     * Mirrors the one-open-batch-per-requester partial unique index. A paused batch stays open: it keeps the
     * requester's one open batch until it is resumed or cancelled.
     */
    public static final Set<BatchStatus> OPEN = EnumSet.of(QUEUED, RUNNING, CANCELLING, PAUSING, PAUSED);
    /** A running batch can be paused; one already pausing or paused answers its state. */
    public static final Set<BatchStatus> PAUSABLE = EnumSet.of(QUEUED, RUNNING);
    /** Stopping or stopped: no child is promoted. */
    public static final Set<BatchStatus> HALTED = EnumSet.of(PAUSING, PAUSED);
    /** A cancel is accepted from every open status but CANCELLING (which already is one). */
    public static final Set<BatchStatus> CANCELLABLE = EnumSet.of(QUEUED, RUNNING, PAUSING, PAUSED);
    public static final Set<BatchStatus> TERMINAL = EnumSet.of(COMPLETED, PARTIALLY_COMPLETED, FAILED, CANCELLED);
}
