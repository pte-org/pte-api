package com.pte.assessment.internal.exception;

import com.pte.assessment.internal.constant.ExamDefinitionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class CustomTemplateActivationDisabledException extends DomainException {

    public CustomTemplateActivationDisabledException() {
        super(HttpStatus.CONFLICT, ExamDefinitionConstants.CUSTOM_TEMPLATE_ACTIVATION_DISABLED,
                null, ExamDefinitionConstants.CUSTOM_TEMPLATE_ACTIVATION_DISABLED_MESSAGE);
    }
}
