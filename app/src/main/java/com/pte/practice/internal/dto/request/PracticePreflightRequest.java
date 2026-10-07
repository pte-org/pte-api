package com.pte.practice.internal.dto.request;

import com.pte.practice.internal.constant.PracticeConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record PracticePreflightRequest(
        @NotBlank(message = PracticeConstants.PRACTICE_PRODUCT_NOT_FOUND)
        String productCode,
        @Size(max = 64) Set<@Size(max = 64) String> taskTypeCodes,
        @Valid PracticeCapabilityManifest capabilities) {

    public Set<String> safeTaskTypeCodes() {
        return taskTypeCodes == null ? Set.of() : Set.copyOf(taskTypeCodes);
    }
}
