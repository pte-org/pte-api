package com.pte.practice.internal.service;

import com.pte.billing.BillingService;
import com.pte.identity.PracticeIdentityService;
import com.pte.identity.PracticeMembershipStatus;
import com.pte.identity.domain.UserStatus;
import com.pte.practice.PracticeEntitlementState;
import com.pte.practice.PracticeObservability;
import com.pte.practice.internal.dto.response.PracticeEntitlementResponse;
import com.pte.practice.internal.exception.PracticeNotEntitledException;
import com.pte.tenancy.StudentQuota;
import com.pte.tenancy.TenantOrganizationSummary;
import com.pte.tenancy.TenancyService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeEntitlementServiceTest {

    @Mock
    private PracticeIdentityService practiceIdentityService;
    @Mock
    private BillingService billingService;
    @Mock
    private TenancyService tenancyService;
    @Mock
    private PracticeObservability observability;

    private PracticeEntitlementService service;
    private UUID shellUserId;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        service = new PracticeEntitlementService(
                practiceIdentityService, billingService, tenancyService, true, observability);
        shellUserId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
    }

    @Test
    void activeImportedStudentWithExamPackageUnlocksPractice() {
        givenMembership(tenantId, UserStatus.ACTIVE, PracticeMembershipStatus.ACTIVE);
        when(tenancyService.getStudentQuota(tenantId)).thenReturn(new StudentQuota(2, 10));
        when(billingService.hasActiveExamPackageSubscription(tenantId)).thenReturn(true);

        PracticeEntitlementResponse result = service.getEntitlement(shellUserId, null);

        assertThat(result.practice().state()).isEqualTo(PracticeEntitlementState.UNLOCKED);
        assertThat(result.organizationContext().publicId()).isEqualTo(tenantId);
    }

    @Test
    void capacityOnlyOrMissingExamPackageRemainsLocked() {
        givenMembership(tenantId, UserStatus.ACTIVE, PracticeMembershipStatus.ACTIVE);
        when(tenancyService.getStudentQuota(tenantId)).thenReturn(new StudentQuota(2, 10));
        when(billingService.hasActiveExamPackageSubscription(tenantId)).thenReturn(false);

        PracticeEntitlementResponse result = service.getEntitlement(shellUserId, null);

        assertThat(result.practice().state()).isEqualTo(PracticeEntitlementState.LOCKED);
    }

    @Test
    void capacityOverrunRemainsLockedEvenWithExamPackage() {
        givenMembership(tenantId, UserStatus.ACTIVE, PracticeMembershipStatus.ACTIVE);
        when(tenancyService.getStudentQuota(tenantId)).thenReturn(new StudentQuota(11, 10));

        PracticeEntitlementResponse result = service.getEntitlement(shellUserId, null);

        assertThat(result.practice().state()).isEqualTo(PracticeEntitlementState.LOCKED);
    }

    @Test
    void inactiveImportedStudentIsUnavailable() {
        givenMembership(tenantId, UserStatus.SUSPENDED, PracticeMembershipStatus.ACTIVE);

        PracticeEntitlementResponse result = service.getEntitlement(shellUserId, null);

        assertThat(result.practice().state()).isEqualTo(PracticeEntitlementState.UNAVAILABLE);
    }

    @Test
    void unknownShellIdentityIsUnavailable() {
        when(practiceIdentityService.resolveForShellUser(shellUserId)).thenReturn(Optional.empty());

        PracticeEntitlementResponse result = service.getEntitlement(shellUserId, null);

        assertThat(result.practice().state()).isEqualTo(PracticeEntitlementState.UNAVAILABLE);
    }

    @Test
    void multipleActiveOrganizationsRequireExplicitContext() {
        UUID secondTenantId = UUID.randomUUID();
        PracticeIdentityService.PracticeIdentityView view = new PracticeIdentityService.PracticeIdentityView(
                UUID.randomUUID(), shellUserId, UserStatus.ACTIVE, List.of(
                        membership(tenantId, UserStatus.ACTIVE, PracticeMembershipStatus.ACTIVE),
                        membership(secondTenantId, UserStatus.ACTIVE, PracticeMembershipStatus.ACTIVE)));
        when(practiceIdentityService.resolveForShellUser(shellUserId)).thenReturn(Optional.of(view));
        when(tenancyService.findActiveTenantIds(anyCollection())).thenReturn(Set.of(tenantId, secondTenantId));
        when(tenancyService.findTenantOrganizationSummaries(anyCollection())).thenReturn(Map.of(
                tenantId, new TenantOrganizationSummary(tenantId, "School A", "School", false),
                secondTenantId, new TenantOrganizationSummary(secondTenantId, "School B", "School", false)));

        PracticeEntitlementResponse result = service.getEntitlement(shellUserId, null);

        assertThat(result.practice().state()).isEqualTo(PracticeEntitlementState.AMBIGUOUS);
        assertThat(result.organizationContext()).isNull();
        assertThat(result.availableOrganizations()).hasSize(2);
    }

    @Test
    void crossOrganizationRequestCannotAuthorizeTheStudent() {
        givenMembership(tenantId, UserStatus.ACTIVE, PracticeMembershipStatus.ACTIVE);
        CurrentUser caller = new CurrentUser(shellUserId, null, List.of("STUDENT"));

        assertThatThrownBy(() -> service.assertUnlocked(caller, UUID.randomUUID()))
                .isInstanceOf(PracticeNotEntitledException.class);
    }

    @Test
    void disabledStrictEntitlementRemainsFailClosed() {
        PracticeEntitlementService disabledService = new PracticeEntitlementService(
                practiceIdentityService, billingService, tenancyService, false, observability);
        CurrentUser caller = new CurrentUser(shellUserId, null, List.of("STUDENT"));

        assertThatThrownBy(() -> disabledService.assertUnlocked(caller, null))
                .isInstanceOf(PracticeNotEntitledException.class);
    }

    @Test
    void removedMembershipDoesNotUnlockPractice() {
        givenMembership(tenantId, UserStatus.ACTIVE, PracticeMembershipStatus.REMOVED);

        PracticeEntitlementResponse result = service.getEntitlement(shellUserId, null);

        assertThat(result.practice().state()).isEqualTo(PracticeEntitlementState.LOCKED);
    }

    private void givenMembership(UUID membershipTenantId, UserStatus userStatus,
            PracticeMembershipStatus membershipStatus) {
        PracticeIdentityService.PracticeIdentityView view = new PracticeIdentityService.PracticeIdentityView(
                UUID.randomUUID(), shellUserId, UserStatus.ACTIVE,
                List.of(membership(membershipTenantId, userStatus, membershipStatus)));
        when(practiceIdentityService.resolveForShellUser(shellUserId)).thenReturn(Optional.of(view));
        if (membershipStatus == PracticeMembershipStatus.ACTIVE && userStatus == UserStatus.ACTIVE) {
            when(tenancyService.findActiveTenantIds(anyCollection())).thenReturn(Set.of(membershipTenantId));
            when(tenancyService.findTenantOrganizationSummaries(anyCollection()))
                    .thenReturn(Map.of(membershipTenantId,
                            new TenantOrganizationSummary(membershipTenantId, "School A", "School", false)));
        }
    }

    private PracticeIdentityService.PracticeMembershipView membership(UUID membershipTenantId,
            UserStatus userStatus, PracticeMembershipStatus membershipStatus) {
        return new PracticeIdentityService.PracticeMembershipView(
                UUID.randomUUID(), membershipTenantId, membershipStatus, userStatus, true);
    }
}
