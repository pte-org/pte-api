package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.session.internal.dto.response.ExamPreflightResponse;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ExamPublishBlockedException extends DomainException {

    public ExamPublishBlockedException(ExamPreflightResponse preflight) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, SessionConstants.EXAM_PREFLIGHT_FAILED, preflight,
                SessionConstants.EXAM_PREFLIGHT_FRIENDLY);
    }
}
