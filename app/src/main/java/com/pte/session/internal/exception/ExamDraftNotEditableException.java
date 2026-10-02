package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ExamDraftNotEditableException extends DomainException {

    public ExamDraftNotEditableException() {
        super(HttpStatus.CONFLICT, SessionConstants.EXAM_DRAFT_NOT_EDITABLE,
                null, SessionConstants.EXAM_DRAFT_NOT_EDITABLE_FRIENDLY);
    }
}
