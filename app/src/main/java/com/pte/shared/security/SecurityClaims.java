package com.pte.shared.security;

/**
 * Canonical JWT claim names. {@code identity} issues tokens with these claims;
 * every module that reads {@link CurrentUser} relies on this contract.
 */
public final class SecurityClaims {

    public static final String TENANT_ID = "tenant_id";
    public static final String ROLES = "roles";

    private SecurityClaims() {
    }
}
