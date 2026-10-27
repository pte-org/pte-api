package com.pte.assessment.internal.exception;

import com.pte.assessment.internal.constant.ExamDefinitionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ExamDefinitionNotFoundException extends DomainException {

    public ExamDefinitionNotFoundException() {
        super(HttpStatus.NOT_FOUND, ExamDefinitionConstants.TEMPLATE_NOT_FOUND);
    }
}
