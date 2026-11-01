package com.pte.attempt.internal.exception;

import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

/** Stable delivery error; the data payload contains capability IDs only, never question content. */
public class ExamCapabilityException extends DomainException {

    public ExamCapabilityException(String code, List<String> missingCapabilities) {
        super(HttpStatus.CONFLICT, code,
                Map.of("missingCapabilities", List.copyOf(missingCapabilities)), userMessage(code), code);
    }

    private static String userMessage(String code) {
        return AttemptConstants.EXAM_REQUIRES_APP_UPDATE.equals(code)
                ? AttemptConstants.EXAM_REQUIRES_APP_UPDATE_MESSAGE
                : AttemptConstants.EXAM_CONFIGURATION_NOT_COMPATIBLE_MESSAGE;
    }
}
