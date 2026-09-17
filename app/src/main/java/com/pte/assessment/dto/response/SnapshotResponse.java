package com.pte.assessment.dto.response;

import java.util.List;
import java.util.UUID;

/** Answer-stripped summary — safe for {@code session} and any human-facing endpoint. */
public record SnapshotResponse(
        UUID publicId,
        String name,
        int version,
        UUID sourceBlueprintPublicId,
        UUID scoreTemplatePublicId,
        int scoreTemplateVersion,
        UUID tenantId,
        List<Item> items) {

    public record Item(int orderIndex, String section, String taskType, String title) {
    }
}
