package com.pte.itembank.internal.generation.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of a generation job. {@code PENDING} is a batch child waiting for its turn (never claimable);
 * {@code CANCELLING} is a cancel request the worker has not yet honoured.
 */
public enum JobStatus {
    PENDING, QUEUED, RUNNING, CANCELLING, COMPLETED, FAILED, CANCELLED;

    /** "In progress" for the one-active-job rule; mirrors the partial unique index, so PENDING stays out. */
    public static final Set<JobStatus> ACTIVE = EnumSet.of(QUEUED, RUNNING, CANCELLING);
    /** Not finished yet, including batch children still waiting. */
    public static final Set<JobStatus> OPEN = EnumSet.of(PENDING, QUEUED, RUNNING, CANCELLING);
    /** A worker holds a live lease; the concurrency cap counts these. */
    public static final Set<JobStatus> LEASED = EnumSet.of(RUNNING, CANCELLING);
    public static final Set<JobStatus> TERMINAL = EnumSet.of(COMPLETED, FAILED, CANCELLED);
}
