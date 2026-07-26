package com.pte.scheduling.client.dto;

import java.util.List;
import java.util.UUID;

/**
 * Client-side view of authoring's snapshot response — deliberately NOT shared
 * with authoring's own DTO (services never share contract classes, only the
 * wire shape). Mirrors {@code SnapshotResponse} in authoring; only the fields
 * scheduling needs (composition metadata, not prompts/answers).
 */
public record AuthoringSnapshotResponse(
        UUID publicId,
        String name,
        int version,
        UUID sourceBlueprintPublicId,
        UUID tenantId,
        List<Item> items) {

    public record Item(int orderIndex, String section, String taskType, String title) {
    }
}
