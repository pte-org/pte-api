package com.pte.itembank.dto.request;

import com.pte.itembank.internal.constant.ItembankConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

/**
 * Create a question. Generic fields validated here; task-type-specific required
 * fields are validated in the service (category-driven).
 */
public record CreateQuestionRequest(
        String pteTaskType,
        String taskTypeKey,
        @NotBlank(message = ItembankConstants.TITLE_REQUIRED) String title,
        String promptText,
        UUID audioPromptRef,
        UUID imagePromptRef,
        String referenceAnswerText,
        String correctAnswerText,
        Integer minWordCount,
        Integer maxWordCount,
        @Valid List<OptionRequest> options) {

    /** Compatibility constructor for legacy clients that send pteTaskType. */
    public CreateQuestionRequest(String pteTaskType, String title, String promptText,
            UUID audioPromptRef, UUID imagePromptRef, String referenceAnswerText,
            String correctAnswerText, Integer minWordCount, Integer maxWordCount,
            List<OptionRequest> options) {
        this(pteTaskType, null, title, promptText, audioPromptRef, imagePromptRef,
                referenceAnswerText, correctAnswerText, minWordCount, maxWordCount, options);
    }
}
