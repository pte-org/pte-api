package com.pte.itembank.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidQuestionTypeException extends DomainException {

    public InvalidQuestionTypeException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_QUESTION_TYPE");
    }
}
