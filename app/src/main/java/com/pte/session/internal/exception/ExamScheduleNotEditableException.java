package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ExamScheduleNotEditableException extends DomainException {

    public ExamScheduleNotEditableException() {
        super(HttpStatus.CONFLICT, SessionConstants.EXAM_SCHEDULE_NOT_EDITABLE, null,
                SessionConstants.EXAM_SCHEDULE_NOT_EDITABLE_FRIENDLY);
    }
}
