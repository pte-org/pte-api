package com.pte.shared.security;

import com.pte.shared.constant.SharedConstants;
import org.springframework.security.access.AccessDeniedException;

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

    /** Per-action authorization for endpoints that fold several transitions into one route. */
    public void requireAnyRole(String... allowed) {
        for (String role : allowed) {
            if (hasRole(role)) {
                return;
            }
        }
        throw new AccessDeniedException(SharedConstants.ACCESS_DENIED);
    }

    public boolean isPlatformUser() {
        return tenantId == null;
    }
}
