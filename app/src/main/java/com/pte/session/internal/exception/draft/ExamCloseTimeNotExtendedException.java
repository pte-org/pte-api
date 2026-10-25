package com.pte.session.internal.exception.draft;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ExamCloseTimeNotExtendedException extends DomainException {

    public ExamCloseTimeNotExtendedException() {
        super(HttpStatus.CONFLICT, SessionConstants.EXAM_CLOSE_TIME_NOT_EXTENDED, null,
                SessionConstants.EXAM_CLOSE_TIME_NOT_EXTENDED_FRIENDLY);
    }
}
