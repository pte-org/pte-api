package com.pte.itembank;

import java.util.List;

/**
 * Semantic, allowlisted runtime contract shared by catalog, templates and
 * clients. It intentionally contains no implementation class or executable
 * configuration.
 */
public record TaskRuntimeContractDescriptor(
        String screenKey,
        int contractVersion,
        String profileKey,
        int profileVersion,
        String behaviorKey,
        String rendererKey,
        int answerSchemaVersion,
        String scoringProfileKey,
        int scoringProfileVersion,
        String scoringMode,
        List<String> requiredClientCapabilities,
        String minSupportedAppVersion,
        String authoringContractKey,
        int authoringContractVersion,
        TaskAuthoringRequirements authoringRequirements,
        String status) {

    public TaskRuntimeContractDescriptor {
        requiredClientCapabilities = requiredClientCapabilities == null
                ? List.of()
                : List.copyOf(requiredClientCapabilities);
    }

    public boolean active() {
        return "ACTIVE".equals(status);
    }

    public boolean scoringEnabled() {
        return "SCORED".equals(scoringMode);
    }
}
