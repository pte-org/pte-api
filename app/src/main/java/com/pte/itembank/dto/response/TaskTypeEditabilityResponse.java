package com.pte.itembank.dto.response;

public record TaskTypeEditabilityResponse(
        boolean taskTypeKey,
        boolean runtimeFields,
        boolean displayMetadata,
        String lockedReason) {
}
