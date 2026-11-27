package com.pte.itembank.internal.generation.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of a generation job. {@code PENDING} is a batch child waiting for its turn (never claimable);
 * {@code CANCELLING} is a cancel request the worker has not yet honoured; {@code PAUSING} a pause request, after which
 * the worker hands the job back to {@code PENDING}.
 */
public enum JobStatus {
    PENDING, QUEUED, RUNNING, CANCELLING, PAUSING, COMPLETED, FAILED, CANCELLED;

    /** "In progress" for the one-active-job rule; mirrors the partial unique index, so PENDING stays out. */
    public static final Set<JobStatus> ACTIVE = EnumSet.of(QUEUED, RUNNING, CANCELLING, PAUSING);
    /** Not finished yet, including batch children still waiting. */
    public static final Set<JobStatus> OPEN = EnumSet.of(PENDING, QUEUED, RUNNING, CANCELLING, PAUSING);
    /** A worker holds a live lease; the concurrency cap counts these. */
    public static final Set<JobStatus> LEASED = EnumSet.of(RUNNING, CANCELLING, PAUSING);
    public static final Set<JobStatus> TERMINAL = EnumSet.of(COMPLETED, FAILED, CANCELLED);
}
