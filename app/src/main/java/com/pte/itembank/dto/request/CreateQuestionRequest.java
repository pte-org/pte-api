package com.pte.itembank.dto.request;

import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

/**
 * Create a question. Generic fields validated here; task-type-specific required
 * fields are validated in the service (category-driven). {@code pool} is
 * {@code EXAM} or {@code PRACTICE}, defaults to {@code EXAM}. The two optional
 * availability flags allow a revision to be explicitly published to either
 * product channel; omitted flags preserve the legacy pool mapping.
 */
public record CreateQuestionRequest(
        String pteTaskType,
        String taskTypeKey,
        String promptText,
        UUID audioPromptRef,
        UUID imagePromptRef,
        String referenceAnswerText,
        String correctAnswerText,
        Integer minWordCount,
        Integer maxWordCount,
        @Valid List<OptionRequest> options,
        String pool,
        Boolean availableForPractice,
        Boolean availableForExam,
        String cefrLevel) {

    /** Constructor for callers that predate the CEFR level. */
    public CreateQuestionRequest(String pteTaskType, String taskTypeKey, String promptText,
            UUID audioPromptRef, UUID imagePromptRef, String referenceAnswerText,
            String correctAnswerText, Integer minWordCount, Integer maxWordCount,
            List<OptionRequest> options, String pool, Boolean availableForPractice, Boolean availableForExam) {
        this(pteTaskType, taskTypeKey, promptText, audioPromptRef, imagePromptRef, referenceAnswerText,
                correctAnswerText, minWordCount, maxWordCount, options, pool, availableForPractice,
                availableForExam, null);
    }

    /** Compatibility constructor for callers that predate the question pool (defaults to EXAM). */
    public CreateQuestionRequest(String pteTaskType, String taskTypeKey, String promptText,
            UUID audioPromptRef, UUID imagePromptRef, String referenceAnswerText,
            String correctAnswerText, Integer minWordCount, Integer maxWordCount,
            List<OptionRequest> options, String pool) {
        this(pteTaskType, taskTypeKey, promptText, audioPromptRef, imagePromptRef,
                referenceAnswerText, correctAnswerText, minWordCount, maxWordCount, options, pool, null, null);
    }

    /** Compatibility constructor for callers that predate the question pool (defaults to EXAM). */
    public CreateQuestionRequest(String pteTaskType, String taskTypeKey, String promptText,
            UUID audioPromptRef, UUID imagePromptRef, String referenceAnswerText,
            String correctAnswerText, Integer minWordCount, Integer maxWordCount,
            List<OptionRequest> options) {
        this(pteTaskType, taskTypeKey, promptText, audioPromptRef, imagePromptRef,
                referenceAnswerText, correctAnswerText, minWordCount, maxWordCount, options, null, null, null);
    }

    /** Compatibility constructor for legacy clients that send pteTaskType. */
    public CreateQuestionRequest(String pteTaskType, String promptText,
            UUID audioPromptRef, UUID imagePromptRef, String referenceAnswerText,
            String correctAnswerText, Integer minWordCount, Integer maxWordCount,
            List<OptionRequest> options) {
        this(pteTaskType, null, promptText, audioPromptRef, imagePromptRef,
                referenceAnswerText, correctAnswerText, minWordCount, maxWordCount, options, null, null, null);
    }
}
