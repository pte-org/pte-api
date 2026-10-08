package com.pte.shared.security;

import java.util.List;
import java.util.UUID;

/**
 * Immutable view of the authenticated principal, derived from validated JWT
 * claims. {@code tenantId} is null for platform-level users.
 */
public record CurrentUser(UUID userId, UUID tenantId, List<String> roles) {

    public CurrentUser {
        roles = SecurityRoles.canonicalizeRoles(roles);
    }

    public boolean hasRole(String role) {
        return SecurityRoles.hasRole(roles, role);
    }

    public boolean isPlatformUser() {
        return tenantId == null;
    }
}
