package com.pte.attempt.internal.service.cache;

import com.pte.itembank.TaskRuntimeProfileDescriptor;

import java.time.Instant;
import java.util.UUID;

/**
 * One task of the frozen exam as the attempt runtime reads it: content from the exam version, timing from the
 * definition it was built from, and presigned media URLs resolved when the content was loaded (never stored with
 * the attempt). {@code publicId} is the exam version item id, stable for the lifetime of the exam.
 * {@code preListenSeconds}/{@code preRecordSeconds} are non-null only for the audio-prompt Speaking task types.
 * The record carries answer keys for scoring; the student-facing mapper strips them.
 */
public record ExamItemView(
        UUID publicId,
        int orderIndex,
        String section,
        String taskType,
        String title,
        String promptText,
        UUID audioPromptRef,
        UUID imagePromptRef,
        String referenceAnswerText,
        String correctAnswerText,
        Integer minWordCount,
        Integer maxWordCount,
        String optionsJson,
        int prepSeconds,
        int responseSeconds,
        Integer preListenSeconds,
        Integer preRecordSeconds,
        String imageUrl,
        String audioUrl,
        Instant audioUrlExpiresAt,
        String taskTypeCode,
        TaskRuntimeProfileDescriptor runtime,
        String runtimeMappingVersion,
        String runtimeMappingStatus,
        String taskTypeDisplayName,
        String taskTypeInstruction) {
}
