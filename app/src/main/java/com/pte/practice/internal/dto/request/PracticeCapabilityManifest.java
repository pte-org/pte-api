package com.pte.practice.internal.dto.request;

import jakarta.validation.constraints.Size;

import java.util.Set;

/** Data-only browser capability manifest; values are matched to server allowlists. */
public record PracticeCapabilityManifest(
        @Size(max = 64) Set<@Size(max = 64) String> supportedCapabilities,
        @Size(max = 32) String appVersion) {

    public Set<String> safeCapabilities() {
        return supportedCapabilities == null ? Set.of() : Set.copyOf(supportedCapabilities);
    }
}
