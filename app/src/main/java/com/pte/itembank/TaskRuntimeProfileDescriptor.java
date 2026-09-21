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
        String status) {

    public TaskRuntimeProfileDescriptor {
        requiredClientCapabilities = requiredClientCapabilities == null
                ? List.of()
                : List.copyOf(requiredClientCapabilities);
    }

    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
