package com.pte.notification.internal.service;

import com.pte.identity.IdentityService;
import com.pte.identity.domain.Role;
import com.pte.notification.domain.enums.InboxNotificationType;
import com.pte.notification.internal.repository.InboxDeliveryRecord;
import com.pte.tenancy.TenancyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboxRecipientEligibilityTest {
    @Mock private IdentityService identity;
    @Mock private TenancyService tenancy;
    private InboxRecipientEligibility eligibility;
    private final UUID user = UUID.randomUUID();
    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        eligibility = new InboxRecipientEligibility(identity, tenancy);
    }

    @Test
    void platformApplicationLocksOnlyActivePlatformAdminWithNullTenant() {
        when(identity.lockActiveRoleMember(user, null, Role.PLATFORM_ADMIN)).thenReturn(true);
        assertThat(eligibility.lockEligible(record(InboxNotificationType.APPLICATION_SUBMITTED, null))).isTrue();
        verify(identity).lockActiveRoleMember(user, null, Role.PLATFORM_ADMIN);
        verifyNoInteractions(tenancy);
    }

    @Test
    void platformApplicationRejectsTenantScopedRecipientBeforeAnyLock() {
        assertThat(eligibility.lockEligible(record(InboxNotificationType.APPLICATION_SUBMITTED, tenant))).isFalse();
        verifyNoInteractions(identity, tenancy);
    }

    @Test
    void inactiveOrWrongRolePlatformMemberIsNotEligible() {
        when(identity.lockActiveRoleMember(user, null, Role.PLATFORM_ADMIN)).thenReturn(false);
        assertThat(eligibility.lockEligible(record(InboxNotificationType.APPLICATION_SUBMITTED, null))).isFalse();
        verify(identity).lockActiveRoleMember(user, null, Role.PLATFORM_ADMIN);
        verifyNoInteractions(tenancy);
    }

    @Test
    void hostLocksActiveTenantBeforeExactActiveHostMembership() {
        when(tenancy.lockActiveTenant(tenant)).thenReturn(true);
        when(identity.lockActiveRoleMember(user, tenant, Role.HOST_ADMIN)).thenReturn(true);
        assertThat(eligibility.lockEligible(record(InboxNotificationType.SESSION_CLOSING_SOON, tenant))).isTrue();
        InOrder ordered = inOrder(tenancy, identity);
        ordered.verify(tenancy).lockActiveTenant(tenant);
        ordered.verify(identity).lockActiveRoleMember(user, tenant, Role.HOST_ADMIN);
    }

    @Test
    void inactiveTenantDoesNotReachMembershipLock() {
        when(tenancy.lockActiveTenant(tenant)).thenReturn(false);
        assertThat(eligibility.lockEligible(record(InboxNotificationType.PLATFORM_ANNOUNCEMENT, tenant))).isFalse();
        verifyNoInteractions(identity);
    }

    @Test
    void nonHostOrInactiveUserCannotReceiveHostNotice() {
        when(tenancy.lockActiveTenant(tenant)).thenReturn(true);
        when(identity.lockActiveRoleMember(user, tenant, Role.HOST_ADMIN)).thenReturn(false);
        assertThat(eligibility.lockEligible(record(InboxNotificationType.ORDER_EXPIRED, tenant))).isFalse();
        verify(identity).lockActiveRoleMember(user, tenant, Role.HOST_ADMIN);
    }

    @Test
    void hostNoticeNeverTreatsNullTenantAsPlatformScope() {
        assertThat(eligibility.lockEligible(record(InboxNotificationType.SUBSCRIPTION_REVOKED, null))).isFalse();
        verifyNoInteractions(identity, tenancy);
    }

    private InboxDeliveryRecord record(InboxNotificationType type, UUID scope) {
        return new InboxDeliveryRecord(UUID.randomUUID(), UUID.randomUUID(), user, scope, type);
    }
}
