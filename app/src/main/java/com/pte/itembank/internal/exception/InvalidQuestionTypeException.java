package com.pte.itembank.internal.exception;

import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidQuestionTypeException extends DomainException {

    public InvalidQuestionTypeException() {
        super(HttpStatus.BAD_REQUEST, ItembankConstants.INVALID_QUESTION_TYPE);
    }
}
