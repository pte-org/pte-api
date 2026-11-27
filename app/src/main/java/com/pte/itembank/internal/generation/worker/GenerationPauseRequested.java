package com.pte.itembank.internal.generation.worker;

import java.util.UUID;

/**
 * A running batch child was asked to pause; published inside the pause transaction and handled after commit, so the
 * worker that holds the job interrupts it only once PAUSING is visible to the runner.
 */
public record GenerationPauseRequested(UUID jobPublicId) {
}
