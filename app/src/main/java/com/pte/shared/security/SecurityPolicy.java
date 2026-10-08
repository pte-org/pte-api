package com.pte.shared.security;

/**
 * Deny-by-default capability bundles for the fixed platform role catalog.
 * Resource ownership and lifecycle rules remain in the owning module.
 */
public final class SecurityPolicy {

    private SecurityPolicy() {
    }

    public static boolean hasCapability(CurrentUser caller, SecurityCapability capability) {
        if (caller == null || capability == null || !hasValidScope(caller)) {
            return false;
        }
        if (caller.hasRole(SecurityRoles.PLATFORM_ADMIN)) {
            return true;
        }
        if (caller.hasRole(SecurityRoles.PLATFORM_MANAGER)) {
            return capability == SecurityCapability.PLATFORM_OPERATIONS;
        }
        if (caller.hasRole(SecurityRoles.ACADEMIC_MANAGER)) {
            return capability == SecurityCapability.ACADEMIC_DRAFT_WRITE
                    || capability == SecurityCapability.ACADEMIC_IMPORT
                    || capability == SecurityCapability.ACADEMIC_REVIEW
                    || capability == SecurityCapability.ACADEMIC_APPROVE
                    || capability == SecurityCapability.ACADEMIC_PUBLISH
                    || capability == SecurityCapability.SCORING_POLICY_MANAGE;
        }
        if (caller.hasRole(SecurityRoles.ACADEMIC_STAFF)) {
            return capability == SecurityCapability.ACADEMIC_DRAFT_WRITE
                    || capability == SecurityCapability.ACADEMIC_IMPORT;
        }
        return false;
    }

    /** Returns true when the principal may start a new academic draft. */
    public static boolean canCreateAcademicDraft(CurrentUser caller) {
        return hasCapability(caller, SecurityCapability.ACADEMIC_DRAFT_WRITE);
    }

    /**
     * Academic drafts are owned by their creator. During the compatibility
     * window, legacy ownerless rows remain editable by academic staff, while
     * review and publish still require a known owner; audit history is not an
     * ownership substitute.
     */
    public static boolean canModifyAcademicDraft(CurrentUser caller, java.util.UUID authorUserPublicId) {
        if (!hasValidScope(caller)) {
            return false;
        }
        if (caller.hasRole(SecurityRoles.PLATFORM_ADMIN)) {
            return true;
        }
        // The legacy PLATFORM_AUTHOR token canonicalizes to ACADEMIC_STAFF.
        // Keep its authoring compatibility window for ownerless legacy drafts;
        // review and publish still require a known owner below.
        if (authorUserPublicId == null && caller.hasRole(SecurityRoles.ACADEMIC_STAFF)) {
            return hasCapability(caller, SecurityCapability.ACADEMIC_DRAFT_WRITE);
        }
        return authorUserPublicId != null
                && authorUserPublicId.equals(caller.userId())
                && hasCapability(caller, SecurityCapability.ACADEMIC_DRAFT_WRITE);
    }

    /**
     * Academic managers review another user's owned draft. Ownerless legacy
     * resources and a manager's own resource require the admin override.
     */
    public static boolean canReviewAcademic(CurrentUser caller, java.util.UUID authorUserPublicId) {
        if (!hasValidScope(caller)) {
            return false;
        }
        if (caller.hasRole(SecurityRoles.PLATFORM_ADMIN)) {
            return true;
        }
        return caller.hasRole(SecurityRoles.ACADEMIC_MANAGER)
                && authorUserPublicId != null
                && !authorUserPublicId.equals(caller.userId())
                && hasCapability(caller, SecurityCapability.ACADEMIC_REVIEW);
    }

    /** Publish/activate uses the same separation-of-duties rule as review. */
    public static boolean canPublishAcademic(CurrentUser caller, java.util.UUID authorUserPublicId) {
        if (!hasValidScope(caller)) {
            return false;
        }
        if (caller.hasRole(SecurityRoles.PLATFORM_ADMIN)) {
            return true;
        }
        return caller.hasRole(SecurityRoles.ACADEMIC_MANAGER)
                && authorUserPublicId != null
                && !authorUserPublicId.equals(caller.userId())
                && hasCapability(caller, SecurityCapability.ACADEMIC_PUBLISH);
    }

    /** Platform roles are tenantless and tenant roles are tenant-bound. */
    public static boolean hasValidScope(CurrentUser caller) {
        if (caller == null || caller.roles().isEmpty()) {
            return false;
        }
        boolean hasPlatformRole = caller.roles().stream().anyMatch(SecurityRoles::isPlatformRole);
        boolean hasTenantRole = caller.roles().stream().anyMatch(SecurityRoles::isTenantRole);
        if (hasPlatformRole == hasTenantRole) {
            return false;
        }
        return hasPlatformRole == (caller.tenantId() == null);
    }
}
