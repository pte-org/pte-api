package com.pte.itembank.dto.response;

import java.util.List;
import java.util.UUID;

import com.pte.itembank.dto.response.QuestionFreezeView.Option;

/** Restricted, pinned evaluation material used after a practice answer is submitted. */
public record PracticeQuestionEvaluationContent(
        UUID publicId,
        String referenceAnswerText,
        String correctAnswerText,
        List<Option> options,
        UUID audioPromptRef,
        UUID imagePromptRef) {

    public PracticeQuestionEvaluationContent {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
