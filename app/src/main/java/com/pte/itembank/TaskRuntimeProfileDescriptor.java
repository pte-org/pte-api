package com.pte.itembank;

import java.util.List;

/**
 * Public, data-only runtime contract for one canonical PTE task type.
 *
 * <p>Keys are allowlisted semantic identifiers. They are deliberately not
 * Java class names, Dart types, scripts or expressions.</p>
 */
public record TaskRuntimeProfileDescriptor(
        String taskTypeCode,
        String profileKey,
        int profileVersion,
        String behaviorKey,
        String rendererKey,
        int answerSchemaVersion,
        String scoringProfileKey,
        int scoringProfileVersion,
        List<String> requiredClientCapabilities,
        String status,
        String screenKey,
        int contractVersion,
        String scoringMode,
        String minSupportedAppVersion,
        String authoringContractKey,
        Integer authoringContractVersion) {

    /** Compatibility constructor for the V45 standard profile shape. */
    public TaskRuntimeProfileDescriptor(String taskTypeCode, String profileKey, int profileVersion,
            String behaviorKey, String rendererKey, int answerSchemaVersion, String scoringProfileKey,
            int scoringProfileVersion, List<String> requiredClientCapabilities, String status) {
        this(taskTypeCode, profileKey, profileVersion, behaviorKey, rendererKey, answerSchemaVersion,
                scoringProfileKey, scoringProfileVersion, requiredClientCapabilities, status,
                rendererKey, profileVersion, "UNSCORED".equals(scoringProfileKey) ? "NONE" : "SCORED",
                "1.0.0", "PTE." + taskTypeCode + "_AUTHORING", 1);
    }

    public TaskRuntimeProfileDescriptor {
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
