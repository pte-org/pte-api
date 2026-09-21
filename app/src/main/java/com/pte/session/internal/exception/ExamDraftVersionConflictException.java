package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ExamDraftVersionConflictException extends DomainException {

    public ExamDraftVersionConflictException() {
        super(HttpStatus.CONFLICT, SessionConstants.EXAM_DRAFT_VERSION_CONFLICT, null,
                SessionConstants.EXAM_DRAFT_VERSION_CONFLICT_FRIENDLY);
    }
}
