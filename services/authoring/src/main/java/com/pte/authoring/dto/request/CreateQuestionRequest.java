package com.pte.authoring.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

/**
 * Create a question. Generic fields validated here; task-type-specific required
 * fields are validated in the service (category-driven).
 */
public record CreateQuestionRequest(
        @NotBlank(message = "Task type is required") String pteTaskType,
        @NotBlank(message = "Visibility is required") String visibility,
        @NotBlank(message = "Title is required") String title,
        String promptText,
        UUID audioPromptRef,
        UUID imagePromptRef,
        String referenceAnswerText,
        String correctAnswerText,
        Integer minWordCount,
        Integer maxWordCount,
        @Valid List<OptionRequest> options) {
}
