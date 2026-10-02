package com.pte.itembank.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class QuestionTypeNotFoundException extends DomainException {

    public QuestionTypeNotFoundException() {
        super(HttpStatus.NOT_FOUND, "QUESTION_TYPE_NOT_FOUND");
    }
}
