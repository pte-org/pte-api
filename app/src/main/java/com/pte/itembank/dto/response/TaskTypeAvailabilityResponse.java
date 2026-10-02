package com.pte.itembank.dto.response;

public record TaskTypeAvailabilityResponse(
        FieldAvailability taskTypeKey,
        FieldAvailability displayName) {

    public record FieldAvailability(String normalized, boolean available, String conflictCode) {
    }
}
