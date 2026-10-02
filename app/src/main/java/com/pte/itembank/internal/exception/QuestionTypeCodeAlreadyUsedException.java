package com.pte.itembank.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class QuestionTypeCodeAlreadyUsedException extends DomainException {

    public QuestionTypeCodeAlreadyUsedException() {
        super(HttpStatus.CONFLICT, "QUESTION_TYPE_CODE_ALREADY_USED");
    }
}
