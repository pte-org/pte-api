package com.pte.itembank.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Mutable content payload accepted only while a question revision is DRAFT. */
public record UpdateQuestionRequest(
        String promptText,
        UUID audioPromptRef,
        UUID imagePromptRef,
        String referenceAnswerText,
        String correctAnswerText,
        Integer minWordCount,
        Integer maxWordCount,
        @Valid List<OptionRequest> options,
        Long version,
        Boolean availableForPractice,
        Boolean availableForExam,
        String cefrLevel,
        @Size(max = 1000) String answerHintVi,
        @Size(max = 1000) String answerHintEn) {

    /** Constructor for callers that predate the answer hints; the hints are then cleared. */
    public UpdateQuestionRequest(String promptText, UUID audioPromptRef, UUID imagePromptRef,
            String referenceAnswerText, String correctAnswerText, Integer minWordCount, Integer maxWordCount,
            List<OptionRequest> options, Long version, Boolean availableForPractice, Boolean availableForExam,
            String cefrLevel) {
        this(promptText, audioPromptRef, imagePromptRef, referenceAnswerText, correctAnswerText, minWordCount,
                maxWordCount, options, version, availableForPractice, availableForExam, cefrLevel, null, null);
    }

    /** Constructor for callers that predate the CEFR level; the level is then left as it is. */
    public UpdateQuestionRequest(String promptText, UUID audioPromptRef, UUID imagePromptRef,
            String referenceAnswerText, String correctAnswerText, Integer minWordCount, Integer maxWordCount,
            List<OptionRequest> options, Long version, Boolean availableForPractice, Boolean availableForExam) {
        this(promptText, audioPromptRef, imagePromptRef, referenceAnswerText, correctAnswerText, minWordCount,
                maxWordCount, options, version, availableForPractice, availableForExam, null);
    }
}
