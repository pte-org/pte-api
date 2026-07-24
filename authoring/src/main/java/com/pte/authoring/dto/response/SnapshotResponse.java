package com.pte.authoring.dto.response;

import java.util.List;
import java.util.UUID;

public record SnapshotResponse(
        UUID publicId,
        String name,
        int version,
        UUID sourceBlueprintPublicId,
        UUID tenantId,
        List<Item> items) {

    public record Item(int orderIndex, String section, String taskType, String title) {
    }
}
