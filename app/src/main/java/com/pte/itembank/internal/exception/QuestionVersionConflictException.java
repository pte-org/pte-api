package com.pte.itembank.internal.exception;

import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class QuestionVersionConflictException extends DomainException {

    public QuestionVersionConflictException() {
        super(HttpStatus.CONFLICT, ItembankConstants.QUESTION_VERSION_CONFLICT);
    }
}
