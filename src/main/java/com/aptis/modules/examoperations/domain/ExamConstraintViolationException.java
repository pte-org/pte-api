package com.aptis.modules.examoperations.domain;

public class ExamConstraintViolationException extends RuntimeException {

    public ExamConstraintViolationException(String message) {
        super(message);
    }

    public ExamConstraintViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
