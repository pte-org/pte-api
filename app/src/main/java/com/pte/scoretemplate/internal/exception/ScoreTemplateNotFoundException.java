package com.pte.scoretemplate.internal.exception;

import com.pte.scoretemplate.internal.constant.ScoreTemplateConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ScoreTemplateNotFoundException extends DomainException {

    public ScoreTemplateNotFoundException() {
        super(HttpStatus.NOT_FOUND, ScoreTemplateConstants.TEMPLATE_NOT_FOUND);
    }
}
