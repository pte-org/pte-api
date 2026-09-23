package com.pte.scoring.dto.response;

import java.util.List;

/** Prompt projection deliberately omits reference answers and all answer-key fields. */
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
        List<ExaminerPromptOptionResponse> options) {
}
