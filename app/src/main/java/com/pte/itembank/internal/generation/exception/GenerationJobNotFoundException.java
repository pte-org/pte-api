package com.pte.itembank.internal.generation.exception;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class GenerationJobNotFoundException extends DomainException {

    public GenerationJobNotFoundException() {
        super(HttpStatus.NOT_FOUND, QuestionGenerationConstants.GENERATION_JOB_NOT_FOUND, null,
                QuestionGenerationConstants.GENERATION_JOB_NOT_FOUND_MESSAGE);
    }
}
