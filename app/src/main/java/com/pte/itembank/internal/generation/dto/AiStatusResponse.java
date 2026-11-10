package com.pte.itembank.internal.generation.dto;

/**
 * What the AI features are set up with, without any secret: whether a key exists is reported, never the key itself,
 * and a provider is shown by its host only.
 */
public record AiStatusResponse(boolean generationEnabled, String generatorModel, String reviewerModel,
                               String providerHost, boolean providerKeyConfigured, String speechHost,
                               boolean speechKeyConfigured, int promptVersion, int supportedTaskTypes,
                               Limits limits) {

    /** The ceilings that keep generation cost bounded. */
    public record Limits(int maxItemsPerJob, int maxItemsPerUserPerDay, int maxItemsPerDayGlobal,
                         int maxConcurrentJobs, int maxCallsPerItem) {
    }
}
