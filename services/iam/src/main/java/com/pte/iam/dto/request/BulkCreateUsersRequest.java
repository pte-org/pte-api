package com.pte.iam.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** Bulk-create Excel-roster rows as STUDENT accounts. {@code tenantId} is honored ONLY for platform callers, same rule as {@link CreateUserRequest}. */
public record BulkCreateUsersRequest(
        @NotEmpty(message = "At least one row is required")
        List<@Valid BulkCreateUserRow> rows,

        UUID tenantId) {
}
