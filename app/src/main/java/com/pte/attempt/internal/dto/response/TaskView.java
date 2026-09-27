package com.pte.attempt.internal.dto.response;

import com.pte.itembank.TaskRuntimeProfileDescriptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Student-facing task content. Deliberately excludes
 * {@code correctAnswerText}/{@code referenceAnswerText} and options'
 * {@code correct} flag — those stay server-side for scoring only.
 * {@code blankGroups} is populated only for {@code FILL_IN_THE_BLANKS_DROPDOWN}
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
        /**
         * The only timing signal per task — for a section-scoped item (READING)
         * this is the live remaining shared-section budget, not just this item's
         * own static slice. No prepDeadline/responseDeadline/serverNow — the
         * client computes its own local countdown entirely from these two ints.
         */
        int prepSeconds,
        int responseSeconds,
        Instant examEndTime,
        /** Non-null only for the 5 audio-prompt Speaking task types — every other task type omits both. */
        Integer preListenSeconds,
        Integer preRecordSeconds,
        /**
         * Resolved, directly-fetchable presigned URL — non-null whenever
         * {@link #imagePromptRef} resolved successfully. No separate on-demand
         * endpoint for images (no replay-limit concern), so embedded directly here.
         */
        String imageUrl,
        /**
         * Resolved presigned audio URL — non-null for LISTENING tasks that have
         * an audio prompt. Listening tasks play this URL directly; Speaking tasks
         * still use the /audio on-demand endpoint (replay-limit enforcement).
         */
        String audioUrl,
        String taskTypeCode,
        TaskRuntimeProfileDescriptor runtime,
        String taskTypeDisplayName,
        boolean canNavigatePrevious,
        boolean canNavigateNext) {

    public TaskView(UUID pinnedItemPublicId, int orderIndex, int totalTasks, String section,
            String taskType, String title, String promptText, UUID audioPromptRef, UUID imagePromptRef,
            Integer minWordCount, Integer maxWordCount, List<OptionView> options,
            List<BlankGroupView> blankGroups, int prepSeconds, int responseSeconds, Instant examEndTime,
            Integer preListenSeconds, Integer preRecordSeconds, String imageUrl) {
        this(pinnedItemPublicId, orderIndex, totalTasks, section, taskType, title, promptText, audioPromptRef,
                imagePromptRef, minWordCount, maxWordCount, options, blankGroups, prepSeconds, responseSeconds,
                examEndTime, preListenSeconds, preRecordSeconds, imageUrl, null, taskType, null, title, false, false);
    }

    public TaskView(UUID pinnedItemPublicId, int orderIndex, int totalTasks, String section,
            String taskType, String title, String promptText, UUID audioPromptRef, UUID imagePromptRef,
            Integer minWordCount, Integer maxWordCount, List<OptionView> options,
            List<BlankGroupView> blankGroups, int prepSeconds, int responseSeconds, Instant examEndTime,
            Integer preListenSeconds, Integer preRecordSeconds, String imageUrl, String taskTypeCode,
            TaskRuntimeProfileDescriptor runtime) {
        this(pinnedItemPublicId, orderIndex, totalTasks, section, taskType, title, promptText, audioPromptRef,
                imagePromptRef, minWordCount, maxWordCount, options, blankGroups, prepSeconds, responseSeconds,
                examEndTime, preListenSeconds, preRecordSeconds, imageUrl, null, taskTypeCode, runtime, title,
                false, false);
    }

    public TaskView(UUID pinnedItemPublicId, int orderIndex, int totalTasks, String section,
            String taskType, String title, String promptText, UUID audioPromptRef, UUID imagePromptRef,
            Integer minWordCount, Integer maxWordCount, List<OptionView> options,
            List<BlankGroupView> blankGroups, int prepSeconds, int responseSeconds, Instant examEndTime,
            Integer preListenSeconds, Integer preRecordSeconds, String imageUrl, String taskTypeCode,
            TaskRuntimeProfileDescriptor runtime, String taskTypeDisplayName) {
        this(pinnedItemPublicId, orderIndex, totalTasks, section, taskType, title, promptText, audioPromptRef,
                imagePromptRef, minWordCount, maxWordCount, options, blankGroups, prepSeconds, responseSeconds,
                examEndTime, preListenSeconds, preRecordSeconds, imageUrl, null, taskTypeCode, runtime,
                taskTypeDisplayName, false, false);
    }
}
