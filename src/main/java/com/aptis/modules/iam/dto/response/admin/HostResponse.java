package com.aptis.modules.iam.dto.response.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record HostResponse(
        Long id,
        String code,
        String name,
        Long organizationId,
        String organizationName,
        String organizationType,
        String address,
        String representativeName,
        String contactEmail,
        String representativePhone,
        String contractCode,
        String packageName,
        Integer studentLimit,
        LocalDate contractStartDate,
        LocalDate contractEndDate,
        LocalDateTime createdAt,
        String initialPassword) {
}
