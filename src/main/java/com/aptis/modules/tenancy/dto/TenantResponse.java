package com.aptis.modules.tenancy.dto;

import java.time.LocalDate;

import com.aptis.modules.iam.domain.enums.UserStatus;

public record TenantResponse(
        Long id,
        String name,
        String type,
        String address,
        String representativeName,
        String representativeEmail,
        String representativePhone,
        String contractCode,
        String packageName,
        Integer studentLimit,
        LocalDate contractStartDate,
        LocalDate contractEndDate,
        UserStatus status) {
}
