package com.pte.itembank.internal.generation.domain;

import java.util.EnumSet;
import java.util.Set;

/** Lifecycle of a generation job. {@code CANCELLING} is a cancel request the worker has not yet honoured. */
public enum JobStatus {
    QUEUED, RUNNING, CANCELLING, COMPLETED, FAILED, CANCELLED;

    /** States that count as "in progress" for the one-active-job rule and the concurrency cap. */
    public static final Set<JobStatus> ACTIVE = EnumSet.of(QUEUED, RUNNING, CANCELLING);
}
