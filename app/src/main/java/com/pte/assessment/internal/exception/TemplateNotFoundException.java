package com.pte.assessment.internal.exception;

import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class TemplateNotFoundException extends DomainException {

    public TemplateNotFoundException() {
        super(HttpStatus.NOT_FOUND, AssessmentConstants.TEMPLATE_NOT_FOUND);
    }
}
