package com.pte.attempt.internal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Bounded, declarative capability identifiers supplied by the installed app. */
public record ClientCapabilityManifest(
        @Size(max = 128, message = "At most 128 capabilities may be declared")
        List<@NotBlank(message = "Capability identifier is required") @Size(max = 96, message = "Capability identifier is too long") String> capabilities,
        @Size(max = 32, message = "App version is too long") String appVersion,
        @Size(max = 256, message = "At most 256 runtime contracts may be declared") List<SupportedRuntimeContract> supportedContracts) {

    public ClientCapabilityManifest {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        supportedContracts = supportedContracts == null ? List.of() : List.copyOf(supportedContracts);
    }

    /** Semantic contract support; never contains widget/class names or code. */
    public record SupportedRuntimeContract(
            @NotBlank @Size(max = 96) String screenKey,
            Integer contractVersion,
            Integer answerSchemaVersion,
            Integer scoringProfileVersion) {
    }

    /** Compatibility constructor for clients and focused tests before Phase 7. */
    public ClientCapabilityManifest(List<String> capabilities) {
        this(capabilities, null, List.of());
    }
}
