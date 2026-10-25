package com.pte.attempt.internal.service.capability;

import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.attempt.internal.dto.response.AttemptPreflightResponse;

import java.util.List;

/** Outcome of matching exam runtime profiles against a client's capabilities. */
record CapabilityCheck(boolean canStart, List<String> missingCapabilities,
        List<AttemptPreflightResponse.UnsupportedTask> unsupportedTasks, String code,
        String userMessage, String fingerprint) {

    static CapabilityCheck compatible(String fingerprint) {
        return new CapabilityCheck(true, List.of(), List.of(), null, null, fingerprint);
    }

    static CapabilityCheck missing(List<String> missingCapabilities) {
        return new CapabilityCheck(false, List.copyOf(missingCapabilities), List.of(),
                AttemptConstants.EXAM_REQUIRES_APP_UPDATE,
                AttemptConstants.EXAM_REQUIRES_APP_UPDATE_MESSAGE, "");
    }

    static CapabilityCheck unsupported(List<String> missingCapabilities,
            List<AttemptPreflightResponse.UnsupportedTask> unsupportedTasks) {
        return new CapabilityCheck(false, List.copyOf(missingCapabilities), List.copyOf(unsupportedTasks),
                AttemptConstants.UNSUPPORTED_RUNTIME_CONTRACT,
                AttemptConstants.UNSUPPORTED_RUNTIME_CONTRACT_MESSAGE, "");
    }

    static CapabilityCheck configuration() {
        return new CapabilityCheck(false, List.of(), List.of(),
                AttemptConstants.EXAM_CONFIGURATION_NOT_COMPATIBLE,
                AttemptConstants.EXAM_CONFIGURATION_NOT_COMPATIBLE_MESSAGE, "");
    }

    AttemptPreflightResponse toResponse() {
        return new AttemptPreflightResponse(canStart, missingCapabilities, unsupportedTasks, code, userMessage);
    }
}
