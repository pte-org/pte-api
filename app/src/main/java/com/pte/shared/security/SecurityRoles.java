package com.pte.shared.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Canonical security-role contract shared by JWT handling and module policies.
 * Persistence still uses identity.domain.Role; this class owns string claims,
 * legacy normalization and platform/tenant scope classification.
 */
public final class SecurityRoles {

    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String PLATFORM_MANAGER = "PLATFORM_MANAGER";
    public static final String ACADEMIC_MANAGER = "ACADEMIC_MANAGER";
    public static final String ACADEMIC_STAFF = "ACADEMIC_STAFF";
    public static final String LEGACY_PLATFORM_AUTHOR = "PLATFORM_AUTHOR";
    public static final String HOST_ADMIN = "HOST_ADMIN";
    public static final String PROCTOR = "PROCTOR";
    public static final String EXAMINER = "EXAMINER";
    public static final String STUDENT = "STUDENT";

    private static final Set<String> PLATFORM_ROLES = Set.of(
            PLATFORM_ADMIN, PLATFORM_MANAGER, ACADEMIC_MANAGER, ACADEMIC_STAFF,
            LEGACY_PLATFORM_AUTHOR);

    private static final Set<String> TENANT_ROLES = Set.of(HOST_ADMIN, PROCTOR, EXAMINER, STUDENT);

    private SecurityRoles() {
    }

    public static String canonicalize(String role) {
        if (role == null) {
            return null;
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        return LEGACY_PLATFORM_AUTHOR.equals(normalized) ? ACADEMIC_STAFF : normalized;
    }

    public static List<String> canonicalizeRoles(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        roles.stream()
                .map(SecurityRoles::canonicalize)
                .filter(value -> value != null && !value.isBlank())
                .forEach(normalized::add);
        return List.copyOf(normalized);
    }

    public static boolean hasRole(Collection<String> roles, String requiredRole) {
        if (roles == null || requiredRole == null) {
            return false;
        }
        String canonicalRequired = canonicalize(requiredRole);
        return roles.stream().map(SecurityRoles::canonicalize).anyMatch(canonicalRequired::equals);
    }

    public static boolean isPlatformRole(String role) {
        return PLATFORM_ROLES.contains(canonicalize(role));
    }

    public static boolean isTenantRole(String role) {
        return TENANT_ROLES.contains(canonicalize(role));
    }

    public static Set<String> platformRoles() {
        return PLATFORM_ROLES;
    }

    public static Set<String> tenantRoles() {
        return TENANT_ROLES;
    }
}
