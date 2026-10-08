package com.pte.shared.security;

import com.pte.shared.constant.SharedConstants;
import org.springframework.security.access.AccessDeniedException;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Common platform action matrix. Modules keep lifecycle and data-scope rules;
 * this policy owns only the role-to-operation delegation boundary.
 */
public final class PlatformOperationPolicy {

    private static final EnumSet<PlatformOperation> MANAGER_OPERATIONS = EnumSet.of(
            PlatformOperation.APPLICATION_READ,
            PlatformOperation.APPLICATION_REVIEW,
            PlatformOperation.TENANT_READ,
            PlatformOperation.TENANT_ONBOARD,
            PlatformOperation.TENANT_LIFECYCLE,
            PlatformOperation.TENANT_BRANDING,
            PlatformOperation.PLAN_READ_ALL,
            PlatformOperation.PLAN_DRAFT_WRITE,
            PlatformOperation.PLAN_PUBLISH,
            PlatformOperation.LICENSE_ISSUE,
            PlatformOperation.LICENSE_MASKED_READ,
            PlatformOperation.COMMERCIAL_READ,
            PlatformOperation.ANNOUNCEMENT_READ,
            PlatformOperation.ANNOUNCEMENT_DRAFT_WRITE,
            PlatformOperation.ANNOUNCEMENT_PUBLISH,
            PlatformOperation.ANNOUNCEMENT_RETRY,
            PlatformOperation.SUPPORT_READ,
            PlatformOperation.SUPPORT_MUTATE);

    private PlatformOperationPolicy() {
    }

    public static boolean can(CurrentUser caller, PlatformOperation operation) {
        if (caller == null || operation == null || caller.userId() == null
                || !SecurityPolicy.hasValidScope(caller)) {
            return false;
        }
        if (caller.hasRole(SecurityRoles.PLATFORM_ADMIN)) {
            return true;
        }
        return caller.hasRole(SecurityRoles.PLATFORM_MANAGER)
                && MANAGER_OPERATIONS.contains(operation);
    }

    public static boolean canAny(CurrentUser caller, PlatformOperation... operations) {
        if (operations == null) {
            return false;
        }
        for (PlatformOperation operation : operations) {
            if (can(caller, operation)) {
                return true;
            }
        }
        return false;
    }

    public static void require(CurrentUser caller, PlatformOperation operation) {
        if (!can(caller, operation)) {
            throw new AccessDeniedException(SharedConstants.ACCESS_DENIED);
        }
    }

    /** Returns true only for the platform-admin override, never for managers. */
    public static boolean isAdmin(CurrentUser caller) {
        return caller != null && caller.userId() != null
                && caller.hasRole(SecurityRoles.PLATFORM_ADMIN)
                && SecurityPolicy.hasValidScope(caller);
    }

    /** Convenience guard for callers that must stay tenant-scoped. */
    public static boolean isPlatformCaller(CurrentUser caller) {
        return caller != null && caller.userId() != null
                && caller.tenantId() == null && SecurityPolicy.hasValidScope(caller);
    }

    /** Keeps platform resource identifiers out of tenant mutation paths. */
    public static boolean samePlatformScope(CurrentUser caller, UUID ignoredTenantId) {
        return isPlatformCaller(caller) && ignoredTenantId == null;
    }
}
