package com.pte.session.internal.dto.response;

import com.pte.itembank.TaskRuntimeProfileDescriptor;

import java.time.Instant;
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
            UUID snapshotItemPublicId,
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
            List<Option> options,
            UUID sourceQuestionPublicId,
            String taskTypeKey,
            TaskRuntimeProfileDescriptor runtime,
            String runtimeMappingVersion,
            String runtimeMappingStatus,
            int prepSeconds,
            int responseSeconds,
            Integer preListenSeconds,
            Integer preRecordSeconds,
            Integer audioDurationSeconds,
            Instant audioUrlExpiresAt,
            Instant imageUrlExpiresAt,
            String taskTypeInstruction) {

        /** Source-compatible constructor for callers using the original answer-free shape. */
        public Item(int orderIndex, String section, String taskType, String taskTypeCode,
                String taskTypeDisplayName, String title, String promptText, String audioUrl,
                String imageUrl, Integer minWordCount, Integer maxWordCount, List<Option> options,
                UUID sourceQuestionPublicId) {
            this(orderIndex, null, section, taskType, taskTypeCode, taskTypeDisplayName, title, promptText,
                    audioUrl, imageUrl, minWordCount, maxWordCount, options, sourceQuestionPublicId,
                    null, null, null, null, 0, 0, null, null, null, null, null, null);
        }
    }

    /** Deliberately excludes option correctness and other scoring metadata. */
    public record Option(int orderIndex, Integer blankIndex, String text) {
    }
}
