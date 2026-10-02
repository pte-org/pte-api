package com.pte.session.internal.dto.response;

import java.util.List;
import java.util.UUID;

/** Host-facing, answer-stripped view of the immutable exam snapshot. */
public record ExamPreviewResponse(
        UUID snapshotPublicId,
        String name,
        int version,
        List<Item> items) {

    public record Item(
            int orderIndex,
            String section,
            String taskType,
            String taskTypeCode,
            String taskTypeDisplayName,
            String title,
            String promptText,
            String audioUrl,
            String imageUrl,
            Integer minWordCount,
            Integer maxWordCount,
            List<Option> options) {
    }

    /** Deliberately excludes option correctness and other scoring metadata. */
    public record Option(int orderIndex, Integer blankIndex, String text) {
    }
}
