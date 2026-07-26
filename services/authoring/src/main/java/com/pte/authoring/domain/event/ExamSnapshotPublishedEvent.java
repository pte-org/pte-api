package com.pte.authoring.domain.event;

import com.pte.authoring.domain.enums.PteSection;
import com.pte.authoring.domain.enums.PteTaskType;

import java.util.List;
import java.util.UUID;

/**
 * Payload for {@code ExamSnapshotPublished}. Carries the composition metadata
 * (which task types/sections it contains, in order) so scheduling can offer
 * full-mock vs practice-subset selection without calling authoring.
 */
public record ExamSnapshotPublishedEvent(
        UUID snapshotPublicId,
        int version,
        String name,
        UUID tenantId,
        List<Item> items) {

    public record Item(int orderIndex, PteSection section, PteTaskType taskType) {
    }
}
