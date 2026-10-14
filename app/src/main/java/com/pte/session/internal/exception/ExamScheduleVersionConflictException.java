package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ExamScheduleVersionConflictException extends DomainException {

    public ExamScheduleVersionConflictException() {
        super(HttpStatus.CONFLICT, SessionConstants.EXAM_SCHEDULE_VERSION_CONFLICT, null,
                SessionConstants.EXAM_SCHEDULE_VERSION_CONFLICT_FRIENDLY);
    }
}
