package com.pte.attempt.internal.dto.response;

import java.util.List;

/** Answer-free capability negotiation result for the student client. */
public record AttemptPreflightResponse(
        boolean canStart,
        List<String> missingCapabilities,
        List<UnsupportedTask> unsupportedTasks,
        String code,
        String userMessage) {

    public AttemptPreflightResponse {
        missingCapabilities = missingCapabilities == null ? List.of() : List.copyOf(missingCapabilities);
        unsupportedTasks = unsupportedTasks == null ? List.of() : List.copyOf(unsupportedTasks);
    }

    public AttemptPreflightResponse(boolean canStart, List<String> missingCapabilities,
            String code, String userMessage) {
        this(canStart, missingCapabilities, List.of(), code, userMessage);
    }

    public record UnsupportedTask(
            String taskTypeKey,
            String screenKey,
            Integer contractVersion,
            String reasonCode,
            String userMessage) {
    }
}
