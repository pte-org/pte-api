package com.pte.assessment.dto.response;

import java.util.List;
import java.util.UUID;

/** Answer-key-free prompt projection for an authorized Examiner work item. */
public record ExaminerQuestionPromptView(
        int orderIndex,
        String section,
        String taskType,
        String title,
        String promptText,
        UUID audioPromptRef,
        UUID imagePromptRef,
        Integer minWordCount,
        Integer maxWordCount,
        List<Option> options) {

    /** No correctness marker or correct-gap index crosses into the Examiner view. */
    public record Option(int orderIndex, String text, Integer blankIndex) {
    }
}
