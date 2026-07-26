package com.pte.common.security;

import java.util.List;
import java.util.UUID;

/**
 * Immutable view of the authenticated principal, derived from validated JWT
 * claims. {@code tenantId} is null for platform-level users. Services read this
 * (never call iam per request) — the token is validated locally via JWKS.
 */
public record CurrentUser(UUID userId, UUID tenantId, List<String> roles) {

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean isPlatformUser() {
        return tenantId == null;
    }
}
