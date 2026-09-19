package com.pte.identity.internal.dto.response;

import java.util.UUID;

/**
 * One-time result of a server-generated credential rotation. The temporary
 * password must never be persisted or returned by a normal user response.
 */
public record GeneratedCredentialsResponse(
        UUID publicId,
        String username,
        String email,
        String fullName,
        String temporaryPassword,
        boolean emailQueued) {
}
