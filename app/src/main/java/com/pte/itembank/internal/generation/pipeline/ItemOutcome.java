package com.pte.itembank.internal.generation.pipeline;

/** What happened to one question; the runner uses it to decide whether the job continues. */
public enum ItemOutcome {
    SAVED, FAILED, FAILED_PROVIDER_AUTH, FAILED_PROVIDER_UNAVAILABLE, CANCELLED, LEASE_LOST,
    /** Stopped by a pause: the question starts over when its batch is resumed. */
    PAUSED,
    /** A person paused or cancelled the job mid-item; the runner reads which from the job. */
    STOPPED
}
