package com.aptis.modules.examoperations.dto;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ExamSettingsRequest(
        @NotNull(message = "skillSubsetReading is required") Boolean skillSubsetReading,
        @NotNull(message = "skillSubsetListening is required") Boolean skillSubsetListening,
        @NotNull(message = "skillSubsetWriting is required") Boolean skillSubsetWriting,
        @NotNull(message = "skillSubsetSpeaking is required") Boolean skillSubsetSpeaking,
        @NotNull(message = "proctorRequired is required") Boolean proctorRequired,
        OffsetDateTime availabilityStart,
        OffsetDateTime availabilityEnd,
        @NotNull(message = "maxRetryCount is required")
        @Min(value = 0, message = "maxRetryCount must be >= 0")
        Integer maxRetryCount,
        @Valid List<TimeOverrideRequest> timeOverrides) {
}
