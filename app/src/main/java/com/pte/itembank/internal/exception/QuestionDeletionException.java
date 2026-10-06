package com.pte.itembank.internal.exception;

import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class QuestionDeletionException extends DomainException {
    public QuestionDeletionException() {
        super(HttpStatus.CONFLICT, ItembankConstants.QUESTION_DELETE_NOT_ALLOWED, null,
                ItembankConstants.QUESTION_DELETE_NOT_ALLOWED_MESSAGE);
    }
}
