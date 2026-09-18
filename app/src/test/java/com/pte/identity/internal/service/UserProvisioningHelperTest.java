package com.pte.identity.internal.service;

import com.pte.identity.domain.Role;
import com.pte.identity.internal.exception.ForbiddenRoleAssignmentException;
import com.pte.identity.internal.exception.ForbiddenUserManagementException;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserProvisioningHelperTest {

    private final UserProvisioningHelper helper = new UserProvisioningHelper();

    private CurrentUser hostAdmin(UUID tenantId) {
        return new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
    }

    private CurrentUser platformAdmin() {
        return new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
    }

    @Test
    void resolveAndAuthorizeRoles_hostCaller_canGrantExaminer() {
        Set<Role> roles = helper.resolveAndAuthorizeRoles(hostAdmin(UUID.randomUUID()), List.of("EXAMINER"));

        assertThat(roles).containsExactly(Role.EXAMINER);
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
    void resolveAndAuthorizeRoles_platformCaller_canOnlyCreateHostAdmin() {
        Set<Role> roles = helper.resolveAndAuthorizeRoles(platformAdmin(), List.of("HOST_ADMIN"));

        assertThat(roles).containsExactly(Role.HOST_ADMIN);
    }

    @Test
    void resolveAndAuthorizeRoles_platformCaller_cannotCreateLowerTenantRole() {
        assertThatThrownBy(() -> helper.resolveAndAuthorizeRoles(platformAdmin(), List.of("STUDENT")))
                .isInstanceOf(ForbiddenRoleAssignmentException.class);
    }

    @Test
    void resolveAndAuthorizeRoles_platformCaller_cannotMixHostWithLowerRole() {
        assertThatThrownBy(() -> helper.resolveAndAuthorizeRoles(
                platformAdmin(), List.of("HOST_ADMIN", "STUDENT")))
                .isInstanceOf(ForbiddenRoleAssignmentException.class);
    }

    @Test
    void resolveAndAuthorizeRoles_unknownRoleName_throws() {
        CurrentUser caller = platformAdmin();

        assertThatThrownBy(() -> helper.resolveAndAuthorizeRoles(caller, List.of("NOT_A_ROLE")))
                .isInstanceOf(ForbiddenRoleAssignmentException.class);
    }

    @Test
    void resolveAndAuthorizeRoles_removedRoleNames_areRejected() {
        CurrentUser caller = hostAdmin(UUID.randomUUID());

        assertThatThrownBy(() -> helper.resolveAndAuthorizeRoles(caller, List.of("HOST_AUTHOR")))
                .isInstanceOf(ForbiddenRoleAssignmentException.class);
        assertThatThrownBy(() -> helper.resolveAndAuthorizeRoles(caller, List.of("LECTURER")))
                .isInstanceOf(ForbiddenRoleAssignmentException.class);
        assertThatThrownBy(() -> helper.resolveAndAuthorizeRoles(caller, List.of("PROGRAM_COORDINATOR")))
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

    @Test
    void validateTenantScope_platformCaller_requiresTenantScopedHostAdmin() {
        UUID tenantId = UUID.randomUUID();

        helper.validateTenantScope(platformAdmin(), tenantId, Set.of(Role.HOST_ADMIN));

        assertThatThrownBy(() -> helper.validateTenantScope(platformAdmin(), null, Set.of(Role.HOST_ADMIN)))
                .isInstanceOf(ForbiddenRoleAssignmentException.class);
    }

    @Test
    void authorizeTarget_platformCaller_canManageHostAdminOnly() {
        helper.authorizeTarget(platformAdmin(), Set.of(Role.HOST_ADMIN));

        assertThatThrownBy(() -> helper.authorizeTarget(platformAdmin(), Set.of(Role.STUDENT)))
                .isInstanceOf(ForbiddenUserManagementException.class);
    }

    @Test
    void authorizeTarget_hostCaller_canManageLowerTenantRoles_butNotHostAdmin() {
        CurrentUser caller = hostAdmin(UUID.randomUUID());

        helper.authorizeTarget(caller, Set.of(Role.STUDENT, Role.PROCTOR, Role.EXAMINER));

        assertThatThrownBy(() -> helper.authorizeTarget(caller, Set.of(Role.HOST_ADMIN)))
                .isInstanceOf(ForbiddenUserManagementException.class);
    }

    @Test
    void authorizeBulkStudentCreation_isHostOnly() {
        helper.authorizeBulkStudentCreation(hostAdmin(UUID.randomUUID()));

        assertThatThrownBy(() -> helper.authorizeBulkStudentCreation(platformAdmin()))
                .isInstanceOf(ForbiddenRoleAssignmentException.class);
    }
}
