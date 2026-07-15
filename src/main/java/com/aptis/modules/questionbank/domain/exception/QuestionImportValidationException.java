package com.aptis.modules.questionbank.domain.exception;

import java.util.List;

import com.aptis.modules.questionbank.dto.response.QuestionImportRowError;

public class QuestionImportValidationException extends RuntimeException {

    private final List<QuestionImportRowError> errors;

    public QuestionImportValidationException(String message, List<QuestionImportRowError> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    public List<QuestionImportRowError> getErrors() {
        return errors;
    }
}
