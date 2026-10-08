package com.pte.shared.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformOperationPolicyTest {

    @Test
    void managerCanUseOnlyMatrixOperations() {
        CurrentUser manager = user(SecurityRoles.PLATFORM_MANAGER, null);

        assertThat(PlatformOperationPolicy.can(manager, PlatformOperation.PLAN_DRAFT_WRITE)).isTrue();
        assertThat(PlatformOperationPolicy.can(manager, PlatformOperation.LICENSE_MASKED_READ)).isTrue();
        assertThat(PlatformOperationPolicy.can(manager, PlatformOperation.COMMERCIAL_READ)).isTrue();
    }

    @Test
    void managerCannotUseAdminOnlyOperations() {
        CurrentUser manager = user(SecurityRoles.PLATFORM_MANAGER, null);

        assertThat(PlatformOperationPolicy.isAdmin(manager)).isFalse();
        assertThat(SecurityPolicy.hasCapability(manager, SecurityCapability.PLATFORM_USER_MANAGE)).isFalse();
        assertThat(SecurityPolicy.hasCapability(manager, SecurityCapability.SECURITY_CONFIGURE)).isFalse();
        assertThat(PlatformOperationPolicy.can(manager, PlatformOperation.PLAN_PUBLISH)).isTrue();
    }

    @Test
    void tenantScopedPrincipalCannotUsePlatformOperations() {
        CurrentUser host = user(SecurityRoles.HOST_ADMIN, UUID.randomUUID());

        assertThat(PlatformOperationPolicy.can(host, PlatformOperation.TENANT_READ)).isFalse();
        assertThat(PlatformOperationPolicy.can(host, PlatformOperation.COMMERCIAL_READ)).isFalse();
    }

    @Test
    void adminCanOverridePlatformOperations() {
        CurrentUser admin = user(SecurityRoles.PLATFORM_ADMIN, null);

        assertThat(PlatformOperationPolicy.can(admin, PlatformOperation.SUPPORT_MUTATE)).isTrue();
        assertThat(PlatformOperationPolicy.isAdmin(admin)).isTrue();
    }

    private CurrentUser user(String role, UUID tenantId) {
        return new CurrentUser(UUID.randomUUID(), tenantId, List.of(role));
    }
}
