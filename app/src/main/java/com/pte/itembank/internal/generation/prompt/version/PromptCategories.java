package com.pte.itembank.internal.generation.prompt.version;

import com.pte.itembank.internal.generation.constant.PromptVersionConstants;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.exception.AiPromptException;
import org.springframework.http.HttpStatus;

import java.util.Locale;

/** Maps the category named in the admin URLs to the kind stored with a prompt, for the prompts that can be edited. */
final class PromptCategories {

    private PromptCategories() {
    }

    static AiPromptKind editableKind(String category) {
        return switch (category) {
            case PromptVersionConstants.CATEGORY_GENERATION -> AiPromptKind.GENERATOR;
            case PromptVersionConstants.CATEGORY_SHARED -> AiPromptKind.SHARED;
            case PromptVersionConstants.CATEGORY_SCORING -> AiPromptKind.SCORER;
            case QuestionGenerationConstants.PROMPT_CATEGORY_REVIEW -> AiPromptKind.REVIEWER;
            case PromptVersionConstants.CATEGORY_CHATBOT -> AiPromptKind.CHATBOT;
            default -> throw new AiPromptException(HttpStatus.BAD_REQUEST,
                    PromptVersionConstants.CATEGORY_NOT_EDITABLE,
                    PromptVersionConstants.CATEGORY_NOT_EDITABLE_MESSAGE);
        };
    }

    /** Task-type keys are stored upper case; the shared block has one fixed key. */
    static String normalizedKey(AiPromptKind kind, String key) {
        return kind == AiPromptKind.SHARED ? QuestionGenerationConstants.PROMPT_KEY_COMMON_GENERATOR
                : key.toUpperCase(Locale.ROOT);
    }

    static String categoryOf(AiPromptKind kind) {
        return switch (kind) {
            case GENERATOR -> PromptVersionConstants.CATEGORY_GENERATION;
            case SHARED -> PromptVersionConstants.CATEGORY_SHARED;
            case REVIEWER -> QuestionGenerationConstants.PROMPT_CATEGORY_REVIEW;
            case SCORER -> QuestionGenerationConstants.PROMPT_CATEGORY_SCORING;
            case CHATBOT -> PromptVersionConstants.CATEGORY_CHATBOT;
        };
    }
}
