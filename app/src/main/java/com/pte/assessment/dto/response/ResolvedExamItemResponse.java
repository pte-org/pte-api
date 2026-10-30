package com.pte.assessment.dto.response;

import com.pte.itembank.TaskRuntimeProfileDescriptor;

import java.time.Instant;
import java.util.UUID;

/**
 * Trusted in-process result of resolving a frozen snapshot item for delivery.
 * It is not a controller response: the session preview maps it to an
 * answer-free DTO, while attempt retains the answer fields for scoring.
 */
public record ResolvedExamItemResponse(
        UUID snapshotItemPublicId,
        UUID sourceQuestionPublicId,
        int orderIndex,
        String section,
        String taskType,
        String taskTypeKey,
        String taskTypeCode,
        String taskTypeDisplayName,
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
        Integer audioDurationSeconds,
        String audioUrl,
        Instant audioUrlExpiresAt,
        String imageUrl,
        Instant imageUrlExpiresAt,
        TaskRuntimeProfileDescriptor runtime,
        String runtimeMappingVersion,
        String runtimeMappingStatus,
        /** Current task-type instruction, resolved live; null when the catalog has none. */
        String taskTypeInstruction) {
}
