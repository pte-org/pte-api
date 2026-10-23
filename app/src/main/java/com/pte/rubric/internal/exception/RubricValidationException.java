package com.pte.rubric.internal.exception;

import com.pte.rubric.internal.constant.RubricConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class RubricValidationException extends DomainException {
    public RubricValidationException(String diagnosticMessage) {
        super(HttpStatus.BAD_REQUEST, codeFor(diagnosticMessage), null,
                diagnosticMessage, diagnosticMessage);
    }

    private static String codeFor(String message) {
        if (RubricConstants.RUBRIC_REASON_REQUIRED.equals(message)) return RubricConstants.RUBRIC_REASON_REQUIRED;
        if (RubricConstants.RUBRIC_MISSING_ANCHOR.equals(message)) return "RUBRIC_MISSING_ANCHOR";
        if (RubricConstants.RUBRIC_INVALID_TASK_TYPE.equals(message)) return "RUBRIC_INVALID_TASK_TYPE";
        if (RubricConstants.RUBRIC_INVALID_STATUS.equals(message)) return "RUBRIC_INVALID_STATUS";
        if (RubricConstants.RUBRIC_RUNTIME_INCOMPATIBLE.equals(message)) return "RUBRIC_RUNTIME_INCOMPATIBLE";
        if (RubricConstants.RUBRIC_INVALID_DOCUMENT.equals(message)) return "RUBRIC_INVALID_DOCUMENT";
        if (RubricConstants.RUBRIC_INVALID_CRITERION.equals(message)) return "RUBRIC_INVALID_CRITERION";
        if (RubricConstants.RUBRIC_INVALID_LEVEL.equals(message)) return "RUBRIC_INVALID_LEVEL";
        return RubricConstants.RUBRIC_VALIDATION_FAILED;
    }
}
