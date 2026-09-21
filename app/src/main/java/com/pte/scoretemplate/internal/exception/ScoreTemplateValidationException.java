package com.pte.scoretemplate.internal.exception;

import com.pte.scoretemplate.internal.constant.ScoreTemplateConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * FR-05 activation validation failed (missing task type, bad count range,
 * negative weight, or a skill with zero total weight). The message carries
 * the specific reason, built by {@code ScoreTemplateActivationValidator}.
 */
public class ScoreTemplateValidationException extends DomainException {

    public ScoreTemplateValidationException(String reason) {
        super(HttpStatus.BAD_REQUEST, ScoreTemplateConstants.TEMPLATE_VALIDATION_FAILED, null, reason, reason);
    }
}
