package com.pte.assessment.internal.exception;

import com.pte.assessment.internal.constant.ExamDefinitionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * FR-05 activation validation failed (missing task type, bad count range,
 * negative weight, or a skill with zero total weight). The message carries
 * the specific reason, built by {@code ScoreTemplateActivationValidator}.
 */
public class ExamDefinitionValidationException extends DomainException {

    public ExamDefinitionValidationException(String reason) {
        super(HttpStatus.BAD_REQUEST, ExamDefinitionConstants.TEMPLATE_VALIDATION_FAILED, null, reason, reason);
    }
}
