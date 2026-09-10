package com.pte.iam.service;

import com.pte.common.security.CurrentUser;
import com.pte.iam.domain.enums.Role;
import com.pte.iam.domain.exception.ForbiddenRoleAssignmentException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * No mock of this class exists anywhere else — {@code UserServiceTest} mocks
 * {@code UserProvisioningHelper} itself, so {@code resolveAndAuthorizeRoles}'s
 * actual allow-list logic was previously untested. First test file for this
 * class (Phase 4 of {@code ninh-multi-type-organization} — added
 * LECTURER/PROGRAM_COORDINATOR to {@code HOST_ASSIGNABLE_ROLES}).
 */
class UserProvisioningHelperTest {

    private final UserProvisioningHelper helper = new UserProvisioningHelper();

    private CurrentUser hostAdmin(UUID tenantId) {
        return new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
    }

    private CurrentUser platformAdmin() {
        return new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
    }

    @Test
    void resolveAndAuthorizeRoles_hostCaller_canGrantLecturer() {
        Set<Role> roles = helper.resolveAndAuthorizeRoles(hostAdmin(UUID.randomUUID()), List.of("LECTURER"));

        assertThat(roles).containsExactly(Role.LECTURER);
    }

    @Test
    void resolveAndAuthorizeRoles_hostCaller_canGrantProgramCoordinator() {
        Set<Role> roles = helper.resolveAndAuthorizeRoles(hostAdmin(UUID.randomUUID()), List.of("PROGRAM_COORDINATOR"));

        assertThat(roles).containsExactly(Role.PROGRAM_COORDINATOR);
    }

    @Test
    void resolveAndAuthorizeRoles_hostCaller_stillCannotGrantPlatformAdmin() {
        CurrentUser caller = hostAdmin(UUID.randomUUID());

        assertThatThrownBy(() -> helper.resolveAndAuthorizeRoles(caller, List.of("PLATFORM_ADMIN")))
                .isInstanceOf(ForbiddenRoleAssignmentException.class);
    }

    @Test
    void resolveAndAuthorizeRoles_hostCaller_stillCannotGrantHostAdmin() {
        CurrentUser caller = hostAdmin(UUID.randomUUID());

        assertThatThrownBy(() -> helper.resolveAndAuthorizeRoles(caller, List.of("HOST_ADMIN")))
                .isInstanceOf(ForbiddenRoleAssignmentException.class);
    }

    @Test
    void resolveAndAuthorizeRoles_platformCaller_unrestrictedIncludingNewRoles() {
        Set<Role> roles = helper.resolveAndAuthorizeRoles(platformAdmin(),
                List.of("PLATFORM_ADMIN", "LECTURER", "PROGRAM_COORDINATOR"));

        assertThat(roles).containsExactlyInAnyOrder(Role.PLATFORM_ADMIN, Role.LECTURER, Role.PROGRAM_COORDINATOR);
    }

    @Test
    void resolveAndAuthorizeRoles_unknownRoleName_throws() {
        CurrentUser caller = platformAdmin();

        assertThatThrownBy(() -> helper.resolveAndAuthorizeRoles(caller, List.of("NOT_A_ROLE")))
                .isInstanceOf(ForbiddenRoleAssignmentException.class);
    }

    @Test
    void resolveTargetTenant_platformCaller_usesRequestedTenant() {
        UUID requestedTenantId = UUID.randomUUID();

        UUID resolved = helper.resolveTargetTenant(platformAdmin(), requestedTenantId);

        assertThat(resolved).isEqualTo(requestedTenantId);
    }

    @Test
    void resolveTargetTenant_hostCaller_forcedIntoOwnTenantIgnoringRequest() {
        UUID callerTenantId = UUID.randomUUID();
        UUID requestedTenantId = UUID.randomUUID();

        UUID resolved = helper.resolveTargetTenant(hostAdmin(callerTenantId), requestedTenantId);

        assertThat(resolved).isEqualTo(callerTenantId);
    }
}
