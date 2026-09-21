package com.pte.assessment.internal.exception;

import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class TemplateNotActiveException extends DomainException {

    public TemplateNotActiveException() {
        super(HttpStatus.CONFLICT, AssessmentConstants.TEMPLATE_NOT_ACTIVE,
                null, "The selected exam template is not active. Choose an active template and try again.");
    }
}
