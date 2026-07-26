package com.pte.examdelivery.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Student-facing task content. Deliberately excludes
 * {@code correctAnswerText}/{@code referenceAnswerText} and options'
 * {@code correct} flag — those stay server-side for scoring only.
 */
public record TaskView(
        UUID pinnedItemPublicId,
        int orderIndex,
        int totalTasks,
        String section,
        String taskType,
        String title,
        String promptText,
        UUID audioPromptRef,
        UUID imagePromptRef,
        Integer minWordCount,
        Integer maxWordCount,
        List<OptionView> options,
        int prepSeconds,
        int responseSeconds,
        Instant prepDeadline,
        Instant responseDeadline,
        Instant serverNow) {
}
