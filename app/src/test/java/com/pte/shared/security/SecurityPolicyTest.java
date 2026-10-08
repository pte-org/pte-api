package com.pte.shared.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPolicyTest {

    private CurrentUser user(UUID tenantId, String role) {
        return new CurrentUser(UUID.randomUUID(), tenantId, List.of(role));
    }

    @Test
    void platformAdmin_hasEveryPlatformCapability() {
        CurrentUser admin = user(null, SecurityRoles.PLATFORM_ADMIN);

        assertThat(SecurityPolicy.hasCapability(admin, SecurityCapability.ROLE_MANAGE)).isTrue();
        assertThat(SecurityPolicy.hasCapability(admin, SecurityCapability.SECURITY_CONFIGURE)).isTrue();
        assertThat(SecurityPolicy.hasCapability(admin, SecurityCapability.ACADEMIC_PUBLISH)).isTrue();
    }

    @Test
    void platformManager_cannotManageRolesOrPublishAcademicContent() {
        CurrentUser manager = user(null, SecurityRoles.PLATFORM_MANAGER);

        assertThat(SecurityPolicy.hasCapability(manager, SecurityCapability.PLATFORM_OPERATIONS)).isTrue();
        assertThat(SecurityPolicy.hasCapability(manager, SecurityCapability.ROLE_MANAGE)).isFalse();
        assertThat(SecurityPolicy.hasCapability(manager, SecurityCapability.ACADEMIC_PUBLISH)).isFalse();
    }

    @Test
    void academicBundles_keepDraftAndGovernanceBoundariesSeparate() {
        CurrentUser staff = user(null, SecurityRoles.ACADEMIC_STAFF);
        CurrentUser manager = user(null, SecurityRoles.ACADEMIC_MANAGER);

        assertThat(SecurityPolicy.hasCapability(staff, SecurityCapability.ACADEMIC_DRAFT_WRITE)).isTrue();
        assertThat(SecurityPolicy.hasCapability(staff, SecurityCapability.ACADEMIC_APPROVE)).isFalse();
        assertThat(SecurityPolicy.hasCapability(manager, SecurityCapability.ACADEMIC_APPROVE)).isTrue();
        assertThat(SecurityPolicy.hasCapability(manager, SecurityCapability.ACADEMIC_PUBLISH)).isTrue();
    }

    @Test
    void invalidMixedScopePrincipal_failsClosed() {
        CurrentUser mixed = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(),
                List.of(SecurityRoles.PLATFORM_MANAGER, SecurityRoles.HOST_ADMIN));

        assertThat(SecurityPolicy.hasCapability(mixed, SecurityCapability.PLATFORM_OPERATIONS)).isFalse();
        assertThat(SecurityPolicy.hasCapability(mixed, SecurityCapability.ROLE_MANAGE)).isFalse();
    }

    @Test
    void academicDraftAndGovernanceUseOwnershipAndSeparationOfDuties() {
        UUID staffId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        CurrentUser staff = new CurrentUser(staffId, null, List.of(SecurityRoles.ACADEMIC_STAFF));
        CurrentUser manager = new CurrentUser(managerId, null, List.of(SecurityRoles.ACADEMIC_MANAGER));
        CurrentUser admin = user(null, SecurityRoles.PLATFORM_ADMIN);

        assertThat(SecurityPolicy.canCreateAcademicDraft(staff)).isTrue();
        assertThat(SecurityPolicy.canModifyAcademicDraft(staff, staffId)).isTrue();
        assertThat(SecurityPolicy.canModifyAcademicDraft(staff, managerId)).isFalse();
        assertThat(SecurityPolicy.canReviewAcademic(manager, staffId)).isTrue();
        assertThat(SecurityPolicy.canReviewAcademic(manager, managerId)).isFalse();
        assertThat(SecurityPolicy.canReviewAcademic(manager, null)).isFalse();
        assertThat(SecurityPolicy.canPublishAcademic(manager, staffId)).isTrue();
        assertThat(SecurityPolicy.canPublishAcademic(admin, null)).isTrue();
    }
}
