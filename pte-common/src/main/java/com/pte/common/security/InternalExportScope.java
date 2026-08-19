package com.pte.common.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.util.UUID;

/**
 * Resolves the tenant scope for a {@code /internal/**\/export} endpoint
 * (rabbitmq-outbox-migration Phase 9, reporting read-model rebuild): a
 * request {@code tenantId} is required UNLESS the caller carries {@link
 * InternalServiceAuth#AUTHORITY_INTERNAL_SERVICE_BOOTSTRAP}, in which case
 * omitting {@code tenantId} means "export ALL tenants." Centralized here,
 * not duplicated per controller, because this is the actual security
 * boundary preventing a normal per-tenant-forwarding call from silently
 * widening into a cross-tenant export.
 */
public final class InternalExportScope {

    private InternalExportScope() {
    }

    /** @return the tenantId to scope the export to, or {@code null} meaning ALL tenants (bootstrap mode only). */
    public static UUID resolve(Authentication authentication, UUID requestedTenantId) {
        if (requestedTenantId != null) {
            return requestedTenantId;
        }
        boolean bootstrapAuthorized = authentication.getAuthorities().stream()
                .anyMatch(authority -> InternalServiceAuth.AUTHORITY_INTERNAL_SERVICE_BOOTSTRAP.equals(authority.getAuthority()));
        if (!bootstrapAuthorized) {
            throw new AccessDeniedException(
                    "tenantId is required unless authorized for the all-tenant bootstrap export mode");
        }
        return null;
    }
}
