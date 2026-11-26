package com.pte.itembank.internal.generation.worker;

import java.util.UUID;

/**
 * A person asked a running job to stop. Published by the cancel services; once the request is committed, the worker
 * that runs the job on this instance interrupts it, so the provider call in progress ends at once instead of
 * running to its end. A job running on another instance still stops at its next step through the CANCELLING status.
 */
public record GenerationCancelRequested(UUID jobPublicId) {
}
