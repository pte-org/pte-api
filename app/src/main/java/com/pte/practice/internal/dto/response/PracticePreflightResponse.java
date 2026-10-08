package com.pte.practice.internal.dto.response;

import java.util.List;

public record PracticePreflightResponse(
        String productCode,
        String catalogVersion,
        boolean ready,
        List<String> requestedTaskTypes,
        List<String> blockedTaskTypes,
        List<String> missingCapabilities) {

    public PracticePreflightResponse {
        requestedTaskTypes = requestedTaskTypes == null ? List.of() : List.copyOf(requestedTaskTypes);
        blockedTaskTypes = blockedTaskTypes == null ? List.of() : List.copyOf(blockedTaskTypes);
        missingCapabilities = missingCapabilities == null ? List.of() : List.copyOf(missingCapabilities);
    }
}
