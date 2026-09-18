package com.pte.identity.internal.dto.request;

import com.pte.identity.internal.constant.IdentityConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** Bulk-create roster rows as STUDENT accounts. This operation is HOST_ADMIN-only. */
public record BulkCreateUsersRequest(
        @NotEmpty(message = IdentityConstants.AT_LEAST_ONE_ROW_REQUIRED)
        List<@Valid BulkCreateUserRow> rows,

        UUID tenantId) {
}
