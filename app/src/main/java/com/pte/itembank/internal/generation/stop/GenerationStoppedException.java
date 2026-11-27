package com.pte.itembank.internal.generation.stop;

/**
 * The job on this thread was asked to stop. This is not a provider failure, so no retry or fallback catches it.
 * The pipeline returns {@code STOPPED}, and the runner reads from the job whether it was a pause or a cancel.
 */
public class GenerationStoppedException extends RuntimeException {

    public GenerationStoppedException() {
        super(null, null, false, false);
    }
}
