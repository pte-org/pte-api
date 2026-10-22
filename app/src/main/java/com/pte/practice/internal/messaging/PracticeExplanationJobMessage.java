package com.pte.practice.internal.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** Queue payload contains only durable job identity, run token and published attempt. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PracticeExplanationJobMessage(UUID jobPublicId, UUID runToken, Integer attemptCount) {
}
