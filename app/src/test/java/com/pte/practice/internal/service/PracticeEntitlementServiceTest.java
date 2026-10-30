package com.pte.practice.internal.service;

import com.pte.billing.BillingService;
import com.pte.identity.IdentityService;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.practice.PracticeEntitlementState;
import com.pte.practice.PracticeObservability;
import com.pte.practice.internal.dto.response.PracticeEntitlementResponse;
import com.pte.practice.internal.exception.PracticeNotEntitledException;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.StudentQuota;
import com.pte.tenancy.TenantOrganizationSummary;
import com.pte.tenancy.TenancyService;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeEntitlementServiceTest {

    @Mock
    private IdentityService identityService;
    @Mock
    private BillingService billingService;
    @Mock
    private TenancyService tenancyService;
    @Mock
    private PracticeObservability observability;

    private PracticeEntitlementService service;
    private UUID studentId;
    private UUID tenantId;
    private CurrentUser caller;

    @BeforeEach
    void setUp() {
        service = new PracticeEntitlementService(
                identityService, billingService, tenancyService, true, observability, com.pte.TestClocks.fixed());
        studentId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        caller = new CurrentUser(studentId, tenantId, List.of("STUDENT"));
    }

    @Test
    void getEntitlement_activeStudentWithExamPackage_unlocksOwnOrganization() {
        givenActiveStudentInActiveTenant();
        when(tenancyService.getStudentQuota(tenantId)).thenReturn(new StudentQuota(2, 10));
        when(billingService.hasActiveExamPackageSubscription(tenantId)).thenReturn(true);

        PracticeEntitlementResponse result = service.getEntitlement(caller, null);

        assertThat(result.practice().state()).isEqualTo(PracticeEntitlementState.UNLOCKED);
        assertThat(result.organizationContext().publicId()).isEqualTo(tenantId);
        assertThat(result.availableOrganizations()).hasSize(1);
        assertThat(result.student().status()).isEqualTo("ACTIVE");
        assertThat(result.refreshAt()).isEqualTo(com.pte.TestClocks.fixed().instant()
                .plusSeconds(com.pte.practice.internal.constant.PracticeConstants.ENTITLEMENT_REFRESH_SECONDS));
    }

    @Test
    void getEntitlement_missingExamPackage_remainsLocked() {
        givenActiveStudentInActiveTenant();
        when(tenancyService.getStudentQuota(tenantId)).thenReturn(new StudentQuota(2, 10));
        when(billingService.hasActiveExamPackageSubscription(tenantId)).thenReturn(false);

        PracticeEntitlementResponse result = service.getEntitlement(caller, null);

        assertThat(result.practice().state()).isEqualTo(PracticeEntitlementState.LOCKED);
    }

    @Test
    void getEntitlement_capacityOverrun_remainsLockedEvenWithExamPackage() {
        givenActiveStudentInActiveTenant();
        when(tenancyService.getStudentQuota(tenantId)).thenReturn(new StudentQuota(11, 10));

        PracticeEntitlementResponse result = service.getEntitlement(caller, null);

        assertThat(result.practice().state()).isEqualTo(PracticeEntitlementState.LOCKED);
    }

    @Test
    void getEntitlement_inactiveOrRemovedStudent_isUnavailable() {
        when(identityService.findActiveStudentInTenant(studentId, tenantId)).thenReturn(Optional.empty());

        PracticeEntitlementResponse result = service.getEntitlement(caller, null);

        assertThat(result.practice().state()).isEqualTo(PracticeEntitlementState.UNAVAILABLE);
        assertThat(result.organizationContext()).isNull();
    }

    @Test
    void getEntitlement_suspendedOrganization_isUnavailable() {
        when(identityService.findActiveStudentInTenant(studentId, tenantId)).thenReturn(Optional.of(student()));
        when(tenancyService.findActiveTenantIds(anyCollection())).thenReturn(Set.of());

        PracticeEntitlementResponse result = service.getEntitlement(caller, null);

        assertThat(result.practice().state()).isEqualTo(PracticeEntitlementState.UNAVAILABLE);
    }

    @Test
    void getEntitlement_tenantlessAccount_isUnavailableWithoutLookups() {
        CurrentUser tenantless = new CurrentUser(studentId, null, List.of("STUDENT"));

        PracticeEntitlementResponse result = service.getEntitlement(tenantless, null);

        assertThat(result.practice().state()).isEqualTo(PracticeEntitlementState.UNAVAILABLE);
        verifyNoInteractions(identityService, tenancyService, billingService);
    }

    @Test
    void getEntitlement_nonStudentRole_isUnavailable() {
        CurrentUser host = new CurrentUser(studentId, tenantId, List.of("HOST_ADMIN"));

        PracticeEntitlementResponse result = service.getEntitlement(host, null);

        assertThat(result.practice().state()).isEqualTo(PracticeEntitlementState.UNAVAILABLE);
        verifyNoInteractions(identityService);
    }

    @Test
    void getEntitlement_otherOrganizationRequested_staysLocked() {
        givenActiveStudentInActiveTenant();

        PracticeEntitlementResponse result = service.getEntitlement(caller, UUID.randomUUID());

        assertThat(result.practice().state()).isEqualTo(PracticeEntitlementState.LOCKED);
    }

    @Test
    void assertUnlocked_crossOrganizationRequest_cannotAuthorizeTheStudent() {
        givenActiveStudentInActiveTenant();

        assertThatThrownBy(() -> service.assertUnlocked(caller, UUID.randomUUID()))
                .isInstanceOf(PracticeNotEntitledException.class);
    }

    @Test
    void assertUnlocked_disabledStrictEntitlement_remainsFailClosed() {
        PracticeEntitlementService disabledService = new PracticeEntitlementService(
                identityService, billingService, tenancyService, false, observability, com.pte.TestClocks.fixed());

        assertThatThrownBy(() -> disabledService.assertUnlocked(caller, tenantId))
                .isInstanceOf(PracticeNotEntitledException.class);
    }

    private void givenActiveStudentInActiveTenant() {
        when(identityService.findActiveStudentInTenant(studentId, tenantId)).thenReturn(Optional.of(student()));
        when(tenancyService.findActiveTenantIds(anyCollection())).thenReturn(Set.of(tenantId));
        when(tenancyService.findTenantOrganizationSummaries(anyCollection())).thenReturn(Map.of(
                tenantId, new TenantOrganizationSummary(tenantId, "School A", "School", false)));
    }

    private User student() {
        User user = new User();
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
