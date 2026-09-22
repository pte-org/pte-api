package com.pte.itembank.dto.response;

import java.util.List;

public record TaskTypeReadinessResponse(
        boolean ready,
        boolean serverReady,
        String clientSupport,
        List<Issue> issues) {

    public TaskTypeReadinessResponse {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public TaskTypeReadinessResponse(boolean serverReady, String clientSupport, List<Issue> issues) {
        this(serverReady, serverReady, clientSupport, issues);
    }

    public record Issue(String code, String message, String remediation) {
    }
}
