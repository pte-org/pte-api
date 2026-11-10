package com.pte.itembank.internal.generation.exception;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class GenerationPromptNotFoundException extends DomainException {

    public GenerationPromptNotFoundException() {
        super(HttpStatus.NOT_FOUND, QuestionGenerationConstants.PROMPT_NOT_FOUND, null,
                QuestionGenerationConstants.PROMPT_NOT_FOUND_MESSAGE);
    }
}
