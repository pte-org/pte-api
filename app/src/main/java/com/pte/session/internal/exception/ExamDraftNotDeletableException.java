package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ExamDraftNotDeletableException extends DomainException {

    public ExamDraftNotDeletableException() {
        super(HttpStatus.CONFLICT, SessionConstants.EXAM_DRAFT_NOT_DELETABLE,
                null, SessionConstants.EXAM_DRAFT_NOT_DELETABLE_FRIENDLY);
    }
}
