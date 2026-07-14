package com.aptis.modules.iam.dto.request.admin;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateHostRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String organizationName,
        @NotBlank String organizationType,
        @NotBlank String address,
        @NotBlank String representativeName,
        @NotBlank @Email String contactEmail,
        @NotBlank String representativePhone,
        String contractCode,
        String packageName,
        @Positive Integer studentLimit,
        LocalDate contractStartDate,
        LocalDate contractEndDate) {
}
