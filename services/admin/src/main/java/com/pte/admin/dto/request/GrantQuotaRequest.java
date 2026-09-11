package com.pte.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Admin grants additional quota to a Tenant — the only action this phase implements. */
public record GrantQuotaRequest(
        @NotBlank(message = "Package name is required")
        String packageName,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive for a grant")
        Integer amount,

        String note) {
}
