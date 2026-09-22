package com.pte.attempt.internal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Bounded, declarative capability identifiers supplied by the installed app. */
public record ClientCapabilityManifest(
        @Size(max = 128, message = "At most 128 capabilities may be declared")
        List<@NotBlank(message = "Capability identifier is required") @Size(max = 96, message = "Capability identifier is too long") String> capabilities) {

    public ClientCapabilityManifest {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }
}
