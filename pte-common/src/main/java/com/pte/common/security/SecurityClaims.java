package com.pte.common.security;

/**
 * Canonical JWT claim names — frozen cross-service contract (iam issues, every
 * resource server reads). Changing these breaks all services at once.
 */
public final class SecurityClaims {

    public static final String TENANT_ID = "tenant_id";
    public static final String ROLES = "roles";

    private SecurityClaims() {
    }
}
