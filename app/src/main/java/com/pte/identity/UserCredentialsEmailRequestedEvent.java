package com.pte.identity;

import java.util.UUID;

/**
 * Published after an operator rotates a user's password and requests a
 * credential email. The temporary password exists only in this in-memory event
 * and the outbound email job; notification history stores a redacted body.
 */
public record UserCredentialsEmailRequestedEvent(
        UUID requestId,
        UUID userPublicId,
        UUID tenantId,
        String username,
        String email,
        String fullName,
        String temporaryPassword) {
}
