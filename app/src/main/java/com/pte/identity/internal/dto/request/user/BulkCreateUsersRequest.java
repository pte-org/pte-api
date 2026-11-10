package com.pte.identity.internal.dto.request.user;

import com.pte.identity.internal.constant.IdentityConstants;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * Bulk-create roster rows as STUDENT accounts. This operation is HOST_ADMIN-only. {@code sendCredentialsEmail}
 * (optional, default false) e-mails each created Student their username and temporary password after the roster
 * is committed.
 */
public record BulkCreateUsersRequest(
        @NotEmpty(message = IdentityConstants.AT_LEAST_ONE_ROW_REQUIRED)
        List<BulkCreateUserRow> rows,

        UUID tenantId,

        Boolean sendCredentialsEmail) {

    public BulkCreateUsersRequest(List<BulkCreateUserRow> rows, UUID tenantId) {
        this(rows, tenantId, null);
    }

    public boolean shouldSendCredentialsEmail() {
        return Boolean.TRUE.equals(sendCredentialsEmail);
    }
}
