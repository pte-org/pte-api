package com.pte.iam.service;

import com.pte.common.security.CurrentUser;
import com.pte.iam.domain.enums.Role;
import com.pte.iam.domain.exception.ForbiddenRoleAssignmentException;
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
            EnumSet.of(Role.HOST_AUTHOR, Role.PROCTOR, Role.STUDENT);

    /**
     * Platform caller → user goes to the requested tenant (may be null for a
     * platform user). Tenant caller → forced into the caller's own tenant,
     * ignoring any tenantId in the request (prevents cross-tenant creation).
     */
    public UUID resolveTargetTenant(CurrentUser caller, UUID requestedTenantId) {
        return caller.isPlatformUser() ? requestedTenantId : caller.tenantId();
    }

    public Set<Role> resolveAndAuthorizeRoles(CurrentUser caller, List<String> roleNames) {
        Set<Role> roles = parseRoles(roleNames);
        if (!caller.isPlatformUser() && !HOST_ASSIGNABLE_ROLES.containsAll(roles)) {
            throw new ForbiddenRoleAssignmentException();
        }
        return roles;
    }

    private Set<Role> parseRoles(List<String> roleNames) {
        try {
            Set<Role> roles = EnumSet.noneOf(Role.class);
            roleNames.forEach(name -> roles.add(Role.valueOf(name)));
            return roles;
        } catch (IllegalArgumentException ex) {
            throw new ForbiddenRoleAssignmentException();
        }
    }
}
