package com.aptis.modules.iam.domain.exception;

import java.util.List;

import com.aptis.modules.iam.domain.studentimport.ImportRowError;

public class ImportValidationException extends RuntimeException {

    private final List<ImportRowError> errors;

    public ImportValidationException(String message, List<ImportRowError> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    public List<ImportRowError> getErrors() {
        return errors;
    }
}
