package com.pte.iam.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Result of a bulk-create call. {@code generatedPassword} is write/return-once —
 * this is the only place it is ever retrievable; it is never echoed by any
 * other endpoint afterward.
 */
public record BulkCreateUsersResponse(List<CreatedUser> created, List<RowError> skipped) {

    public record CreatedUser(UUID publicId, String email, String fullName, String generatedPassword) {
    }

    /** {@code reason} is one of IamConstants' error codes (e.g. EMAIL_ALREADY_USED). */
    public record RowError(int rowIndex, String email, String reason) {
    }
}
