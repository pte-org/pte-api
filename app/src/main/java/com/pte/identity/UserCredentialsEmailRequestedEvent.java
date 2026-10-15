package com.pte.identity;

import java.util.UUID;

/**
 * Published after a user's temporary password is generated or rotated and a
 * credential email is requested. The temporary password exists only in this
 * in-memory event and the outbound email job; notification history stores a
 * redacted body.
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
