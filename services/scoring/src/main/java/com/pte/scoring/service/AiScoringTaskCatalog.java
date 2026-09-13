package com.pte.scoring.service;

import com.pte.scoring.constant.ScoringConstants;

import java.util.Set;

/**
 * Single catalog for AI-shaped PTE tasks and their vendor modality.
 * Deterministic objective tasks are intentionally not listed here.
 */
public final class AiScoringTaskCatalog {

    private static final Set<String> SPEECH_TASK_TYPES = Set.of(
            ScoringConstants.TASK_TYPE_READ_ALOUD,
            ScoringConstants.TASK_TYPE_REPEAT_SENTENCE,
            ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE,
            ScoringConstants.TASK_TYPE_RE_TELL_LECTURE,
            ScoringConstants.TASK_TYPE_ANSWER_SHORT_QUESTION,
            ScoringConstants.TASK_TYPE_RESPOND_TO_A_SITUATION,
            ScoringConstants.TASK_TYPE_SUMMARIZE_GROUP_DISCUSSION);

    private static final Set<String> TEXT_TASK_TYPES = Set.of(
            ScoringConstants.TASK_TYPE_WRITE_ESSAY,
            ScoringConstants.TASK_TYPE_SUMMARIZE_WRITTEN_TEXT,
            ScoringConstants.TASK_TYPE_SUMMARIZE_SPOKEN_TEXT);

    private AiScoringTaskCatalog() {
    }

    public static boolean supports(String taskType) {
        return isSpeech(taskType) || isText(taskType);
    }

    public static boolean isSpeech(String taskType) {
        return taskType != null && SPEECH_TASK_TYPES.contains(taskType);
    }

    public static boolean isText(String taskType) {
        return taskType != null && TEXT_TASK_TYPES.contains(taskType);
    }
}
