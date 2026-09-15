package com.pte.itembank.internal.exception;

import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class QuestionNotFoundException extends DomainException {

    public QuestionNotFoundException() {
        super(HttpStatus.NOT_FOUND, ItembankConstants.QUESTION_NOT_FOUND);
    }
}
