package com.pte.scoretemplate.internal.exception;

import com.pte.scoretemplate.internal.constant.ScoreTemplateConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class CustomTemplateActivationDisabledException extends DomainException {

    public CustomTemplateActivationDisabledException() {
        super(HttpStatus.CONFLICT, ScoreTemplateConstants.CUSTOM_TEMPLATE_ACTIVATION_DISABLED,
                null, ScoreTemplateConstants.CUSTOM_TEMPLATE_ACTIVATION_DISABLED_MESSAGE);
    }
}
