package com.pte.itembank.internal.generation.exception;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** The feature flag is off; the web disables the entry point with the same code. */
public class GenerationDisabledException extends DomainException {

    public GenerationDisabledException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, QuestionGenerationConstants.GENERATION_DISABLED, null,
                QuestionGenerationConstants.GENERATION_DISABLED_MESSAGE);
    }
}
