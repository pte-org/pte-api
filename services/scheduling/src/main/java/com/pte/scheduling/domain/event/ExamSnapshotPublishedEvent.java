package com.pte.scheduling.domain.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/**
 * scheduling's own view of authoring's {@code ExamSnapshotPublished} payload
 * (§3: never share producer/consumer DTOs across services). {@code section}/
 * {@code taskType} are plain strings, matching {@code SnapshotRefItem} — no
 * shared business enum across the service boundary.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExamSnapshotPublishedEvent(UUID snapshotPublicId, int version, String name, UUID tenantId, List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(int orderIndex, String section, String taskType) {
    }
}
