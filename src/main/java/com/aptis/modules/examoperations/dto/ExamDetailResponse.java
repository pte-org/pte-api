package com.aptis.modules.examoperations.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ExamDetailResponse(
        Long id,
        String name,
        String code,
        Boolean isAssignable,
        Boolean skillSubsetReading,
        Boolean skillSubsetListening,
        Boolean skillSubsetWriting,
        Boolean skillSubsetSpeaking,
        Boolean proctorRequired,
        OffsetDateTime availabilityStart,
        OffsetDateTime availabilityEnd,
        Integer maxRetryCount,
        List<TimeOverrideResponse> timeOverrides,
        boolean startable) {
}
