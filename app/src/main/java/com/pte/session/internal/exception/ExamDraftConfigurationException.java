package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ExamDraftConfigurationException extends DomainException {

    public ExamDraftConfigurationException(String reason) {
        super(HttpStatus.BAD_REQUEST, SessionConstants.EXAM_DRAFT_CONFIGURATION_INVALID, null, reason);
    }
}
