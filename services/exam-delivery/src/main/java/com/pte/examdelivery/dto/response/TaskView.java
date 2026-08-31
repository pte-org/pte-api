package com.pte.examdelivery.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Student-facing task content. Deliberately excludes
 * {@code correctAnswerText}/{@code referenceAnswerText} and options'
 * {@code correct} flag — those stay server-side for scoring only.
 * {@code blankGroups} is populated only for {@code FILL_BLANKS_READING_WRITING}
 * (each blank has its own distinct option list); {@code options} carries every
 * other task type's flat choice/word-bank/paragraph list. The two are mutually
 * exclusive per task — never both populated at once.
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
        List<BlankGroupView> blankGroups,
        int prepSeconds,
        int responseSeconds,
        Instant prepDeadline,
        Instant responseDeadline,
        Instant serverNow,
        Instant examEndTime,
        /**
         * Non-null only for the 5 audio-prompt Speaking task types — every
         * other task type omits both (plans/phat-speaking-dynamic-prep-timing).
         */
        Integer preListenSeconds,
        Integer preRecordSeconds) {
}
