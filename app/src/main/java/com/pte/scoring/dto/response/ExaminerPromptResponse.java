package com.pte.scoring.dto.response;

import java.util.List;

/**
 * Prompt projection for the examiner, with the item's answer key: reference answer (or audio transcript), correct
 * answer and option keys. Internal identifiers, revisions and pool are left out; the Student never receives it.
 */
public record ExaminerPromptResponse(
        int orderIndex,
        String section,
        String taskType,
        String title,
        String promptText,
        String audioPromptUrl,
        String imagePromptUrl,
        Integer minWordCount,
        Integer maxWordCount,
        List<ExaminerPromptOptionResponse> options,
        /** CEFR level of the bank question (A1..C2); null when the question has none. */
        String cefrLevel,
        String referenceAnswerText,
        String correctAnswerText) {

    public ExaminerPromptResponse(int orderIndex, String section, String taskType, String title, String promptText,
            String audioPromptUrl, String imagePromptUrl, Integer minWordCount, Integer maxWordCount,
            List<ExaminerPromptOptionResponse> options, String cefrLevel) {
        this(orderIndex, section, taskType, title, promptText, audioPromptUrl, imagePromptUrl, minWordCount,
                maxWordCount, options, cefrLevel, null, null);
    }

    public ExaminerPromptResponse(int orderIndex, String section, String taskType, String title, String promptText,
            String audioPromptUrl, String imagePromptUrl, Integer minWordCount, Integer maxWordCount,
            List<ExaminerPromptOptionResponse> options) {
        this(orderIndex, section, taskType, title, promptText, audioPromptUrl, imagePromptUrl, minWordCount,
                maxWordCount, options, null, null, null);
    }
}
