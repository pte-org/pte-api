package com.pte.shared.exception.exam;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

/** Stable delivery error; the data payload contains capability IDs only, never question content. */
public class ExamCapabilityException extends DomainException {

    public static final String REQUIRES_APP_UPDATE = "EXAM_REQUIRES_APP_UPDATE";
    public static final String REQUIRES_APP_UPDATE_MESSAGE =
            "Please update the PTE Prep app before starting this exam. It requires capabilities that this app does not provide.";
    public static final String NOT_COMPATIBLE_MESSAGE =
            "This exam is not currently compatible with the configured task runtime. Please ask an administrator to review the exam configuration.";

    public ExamCapabilityException(String code, List<String> missingCapabilities) {
        super(HttpStatus.CONFLICT, code,
                Map.of("missingCapabilities", List.copyOf(missingCapabilities)), userMessage(code), code);
    }

    private static String userMessage(String code) {
        return REQUIRES_APP_UPDATE.equals(code) ? REQUIRES_APP_UPDATE_MESSAGE : NOT_COMPATIBLE_MESSAGE;
    }
}
