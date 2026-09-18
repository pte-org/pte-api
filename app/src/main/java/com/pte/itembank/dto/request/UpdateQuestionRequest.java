package com.pte.itembank.dto.request;

import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

/** Mutable content payload accepted only while a question revision is DRAFT. */
public record UpdateQuestionRequest(
        String title,
        String promptText,
        UUID audioPromptRef,
        UUID imagePromptRef,
        String referenceAnswerText,
        String correctAnswerText,
        Integer minWordCount,
        Integer maxWordCount,
        @Valid List<OptionRequest> options,
        Long version) {
}
