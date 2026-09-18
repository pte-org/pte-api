package com.pte.itembank.internal.exception;

import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Requested a status change the transition table (DRAFT/PUBLISHED/ARCHIVED) does not allow. */
public class InvalidQuestionStatusTransitionException extends DomainException {

    public InvalidQuestionStatusTransitionException() {
        super(HttpStatus.CONFLICT, ItembankConstants.INVALID_QUESTION_STATUS_TRANSITION);
    }
}
