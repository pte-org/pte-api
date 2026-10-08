package com.pte.identity.internal.service;

import com.pte.identity.domain.Role;
import com.pte.identity.internal.exception.ForbiddenRoleAssignmentException;
import com.pte.identity.internal.exception.ForbiddenUserManagementException;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityCapability;
import com.pte.shared.security.SecurityPolicy;
import com.pte.shared.security.SecurityRoles;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves the target tenant and validates role assignment for user creation,
 * keeping {@link UserService} within its method budget. Enforces that a
 * tenant-scoped caller (host) can only create tenant-level users in its own tenant.
 */
@Component
public class UserProvisioningHelper {

    /** Roles a tenant-scoped caller (HOST_ADMIN) is allowed to assign. */
    private static final Set<Role> HOST_ASSIGNABLE_ROLES =
            EnumSet.of(Role.PROCTOR, Role.EXAMINER, Role.STUDENT);

    /** The legacy user endpoint may provision only a tenant root for a platform admin. */
    private static final Set<Role> PLATFORM_ASSIGNABLE_ROLES = Set.of(Role.HOST_ADMIN);
    private static final Set<Role> PLATFORM_ROLE_ASSIGNABLE_ROLES = EnumSet.of(
            Role.PLATFORM_MANAGER, Role.ACADEMIC_MANAGER, Role.ACADEMIC_STAFF);

    /** The same hierarchy is used for user lookup and lifecycle management. */
    private static final Set<Role> HOST_MANAGEABLE_ROLES = HOST_ASSIGNABLE_ROLES;
    private static final Set<Role> PLATFORM_MANAGEABLE_ROLES = PLATFORM_ASSIGNABLE_ROLES;

    /**
     * Platform admin → user goes to the requested tenant. Host admin → forced
     * into the caller's own tenant, ignoring any tenantId in the request.
     */
    public UUID resolveTargetTenant(CurrentUser caller, UUID requestedTenantId) {
        if (isPlatformAdmin(caller)) {
            return requestedTenantId;
        }
        if (isHostAdmin(caller)) {
            return caller.tenantId();
        }
        throw new ForbiddenUserManagementException();
    }

    public Set<Role> resolveAndAuthorizeRoles(CurrentUser caller, List<String> roleNames) {
        Set<Role> roles = parseRoles(roleNames);
        Set<Role> allowedRoles = isPlatformAdmin(caller)
                ? PLATFORM_ASSIGNABLE_ROLES
                : isHostAdmin(caller) ? HOST_ASSIGNABLE_ROLES : Set.of();
        if (roles.isEmpty() || !allowedRoles.containsAll(roles)) {
            throw new ForbiddenRoleAssignmentException();
        }
        return roles;
    }

    public void authorizePlatformUserManagement(CurrentUser caller) {
        if (!SecurityPolicy.hasCapability(caller, SecurityCapability.PLATFORM_USER_MANAGE)) {
            throw new ForbiddenUserManagementException();
        }
    }

    public Set<Role> resolvePlatformRoles(CurrentUser caller, List<String> roleNames) {
        authorizePlatformUserManagement(caller);
        Set<Role> roles = parseRoles(roleNames);
        if (roles.isEmpty() || !PLATFORM_ROLE_ASSIGNABLE_ROLES.containsAll(roles)) {
            throw new ForbiddenRoleAssignmentException();
        }
        return roles;
    }

    public void authorizePlatformTarget(CurrentUser caller, Set<Role> targetRoles) {
        authorizePlatformUserManagement(caller);
        if (!isPlatformTarget(targetRoles) || targetRoles.contains(Role.PLATFORM_ADMIN)) {
            throw new ForbiddenUserManagementException();
        }
    }

    public boolean isPlatformTarget(Set<Role> targetRoles) {
        return targetRoles != null && !targetRoles.isEmpty()
                && targetRoles.stream().allMatch(role -> SecurityRoles.isPlatformRole(role.name()));
    }

    public Set<Role> platformRoleValues() {
        return EnumSet.of(Role.PLATFORM_ADMIN, Role.PLATFORM_MANAGER, Role.ACADEMIC_MANAGER,
                Role.ACADEMIC_STAFF, Role.PLATFORM_AUTHOR);
    }

    /** Bulk user creation is a student-provisioning operation owned by a Host. */
    public void authorizeBulkStudentCreation(CurrentUser caller) {
        if (!isHostAdmin(caller)) {
            throw new ForbiddenRoleAssignmentException();
        }
    }

    /** Ensures a newly provisioned role set has the correct tenant scope. */
    public void validateTenantScope(CurrentUser caller, UUID tenantId, Set<Role> roles) {
        if (isPlatformAdmin(caller)) {
            if (tenantId == null || !PLATFORM_ASSIGNABLE_ROLES.containsAll(roles)) {
                throw new ForbiddenRoleAssignmentException();
            }
            return;
        }
        if (!isHostAdmin(caller) || tenantId == null || !HOST_ASSIGNABLE_ROLES.containsAll(roles)) {
            throw new ForbiddenRoleAssignmentException();
        }
    }

    /** Throws unless the caller may operate on the target user's role set. */
    public void authorizeTarget(CurrentUser caller, Set<Role> targetRoles) {
        if (!canManageTarget(caller, targetRoles)) {
            throw new ForbiddenUserManagementException();
        }
    }

    public boolean canManageTarget(CurrentUser caller, Set<Role> targetRoles) {
        if (targetRoles == null || targetRoles.isEmpty()) {
            return false;
        }
        Set<Role> allowedRoles = isPlatformAdmin(caller)
                ? PLATFORM_MANAGEABLE_ROLES
                : isHostAdmin(caller) ? HOST_MANAGEABLE_ROLES : Set.of();
        return allowedRoles.containsAll(targetRoles);
    }

    public void authorizePlatformTenantListing(CurrentUser caller) {
        if (!isPlatformAdmin(caller)) {
            throw new ForbiddenUserManagementException();
        }
    }

    private boolean isPlatformAdmin(CurrentUser caller) {
        return caller != null && SecurityPolicy.hasValidScope(caller)
                && caller.hasRole(Role.PLATFORM_ADMIN.name()) && caller.tenantId() == null;
    }

    private boolean isHostAdmin(CurrentUser caller) {
        return caller != null && SecurityPolicy.hasValidScope(caller)
                && caller.hasRole(Role.HOST_ADMIN.name()) && caller.tenantId() != null;
    }

    private Set<Role> parseRoles(List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return Set.of();
        }
        try {
            Set<Role> roles = EnumSet.noneOf(Role.class);
            roleNames.forEach(name -> {
                String canonicalName = SecurityRoles.canonicalize(name);
                if (canonicalName == null || canonicalName.isBlank()) {
                    throw new ForbiddenRoleAssignmentException();
                }
                roles.add(Role.valueOf(canonicalName));
            });
            return roles;
        } catch (IllegalArgumentException ex) {
            throw new ForbiddenRoleAssignmentException();
        }
    }

}
