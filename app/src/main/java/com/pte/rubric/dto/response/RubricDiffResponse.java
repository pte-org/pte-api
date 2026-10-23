package com.pte.rubric.dto.response;

import java.util.List;
import java.util.UUID;

/** Stable, review-friendly summary of fields changed between two revisions. */
public record RubricDiffResponse(
        UUID fromPublicId,
        int fromVersion,
        UUID toPublicId,
        int toVersion,
        boolean canonicalChanged,
        List<String> changedPaths) {
}
