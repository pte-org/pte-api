package com.pte.rubric;

import java.util.UUID;

/** Safe immutable rubric projection exposed to Practice runtime consumers. */
public record RubricPublishedProjection(
        UUID publicId,
        String rubricKey,
        String taskTypeKey,
        int revision,
        String canonicalHash,
        String documentJson,
        String runtimeCompatibilityHash) {
}
