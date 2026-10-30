package com.pte.practice.internal.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** Queue payload carries identity and the dispatch token only; the answer is read from the item row. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PracticeScoringJob(UUID itemPublicId, UUID tenantId, UUID dispatchToken) {
}
