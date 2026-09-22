package com.pte.attempt.internal.dto.response;

import java.util.List;

/** Answer-free capability negotiation result for the student client. */
public record AttemptPreflightResponse(
        boolean canStart,
        List<String> missingCapabilities,
        String code,
        String userMessage) {

    public AttemptPreflightResponse {
        missingCapabilities = missingCapabilities == null ? List.of() : List.copyOf(missingCapabilities);
    }
}
