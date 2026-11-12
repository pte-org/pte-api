package com.pte.itembank.internal.generation.dto;

/**
 * What the AI features are set up with, without any secret: whether a key exists is reported, never the key itself,
 * and a provider is shown by its host and a link to its web page (no user info or query, where a key could sit).
 */
public record AiStatusResponse(boolean generationEnabled, String generatorModel, String reviewerModel,
                               String providerHost, String providerWebUrl, boolean providerKeyConfigured,
                               String speechHost, String speechWebUrl, boolean speechKeyConfigured, int promptVersion, int supportedTaskTypes,
                               Limits limits) {

    /** The ceilings that keep generation cost bounded. */
    public record Limits(int maxItemsPerJob, int maxItemsPerUserPerDay, int maxItemsPerDayGlobal,
                         int maxConcurrentJobs, int maxCallsPerItem) {
    }
}
