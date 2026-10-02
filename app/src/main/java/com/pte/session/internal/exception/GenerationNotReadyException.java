package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class GenerationNotReadyException extends DomainException {

    public GenerationNotReadyException() {
        super(HttpStatus.CONFLICT, SessionConstants.EXAM_GENERATION_NOT_READY, null,
                SessionConstants.EXAM_GENERATION_FRIENDLY);
    }
}
