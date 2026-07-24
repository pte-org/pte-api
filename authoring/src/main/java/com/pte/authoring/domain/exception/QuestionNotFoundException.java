package com.pte.authoring.domain.exception;

import com.pte.authoring.constant.AuthoringConstants;
import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class QuestionNotFoundException extends DomainException {

    public QuestionNotFoundException() {
        super(HttpStatus.NOT_FOUND, AuthoringConstants.QUESTION_NOT_FOUND);
    }
}
