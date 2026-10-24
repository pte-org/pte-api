package com.pte.tenancy.internal.service;

import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.enums.FacilityType;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import com.pte.tenancy.TenantSuspensionImpact;
import com.pte.tenancy.internal.exception.TenantNameAlreadyUsedException;
import com.pte.tenancy.internal.exception.TenantNotFoundException;
import com.pte.tenancy.internal.exception.TenantSuspensionDaysInvalidException;
import com.pte.tenancy.internal.exception.TenantTaxCodeAlreadyUsedException;
import com.pte.tenancy.internal.dto.request.OnboardTenantRequest;
import com.pte.tenancy.internal.dto.request.SuspendTenantRequest;
import com.pte.tenancy.internal.dto.request.UpdateBrandingRequest;
import com.pte.tenancy.internal.exception.TenantSuspensionImpactStaleException;
import com.pte.tenancy.internal.dto.response.OrganizationResponse;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationLifecycleServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @org.mockito.Mock
    private com.pte.tenancy.TenantSuspensionImpactProvider suspensionImpactProvider;

    private OrganizationLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new OrganizationLifecycleService(organizationRepository);
    }

    private Organization activeTenant(UUID publicId) {
        Organization tenant = new Organization();
        tenant.setPublicId(publicId);
        tenant.setCode("acme-school");
        tenant.setName("Acme School");
        tenant.setOrganizationType("SCHOOL");
        tenant.setPackageName("starter");
        tenant.setStudentLimit(500);
        tenant.setStatus(OrganizationStatus.ACTIVE);
        return tenant;
    }

    @Test
    void onboard_savesTenant() {
        OnboardTenantRequest request = new OnboardTenantRequest("acme-school", "Acme School", "SCHOOL",
                "0123456789", "starter", 500);
        when(organizationRepository.existsByNameAndStatusNot("Acme School", OrganizationStatus.REJECTED)).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> {
            Organization saved = invocation.getArgument(0);
            saved.setPublicId(UUID.randomUUID());
            return saved;
        });

        OrganizationResponse response = service.onboard(request);

        assertThat(response.name()).isEqualTo("Acme School");
        assertThat(response.status()).isEqualTo("ACTIVE");

        ArgumentCaptor<Organization> savedOrganization = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(savedOrganization.capture());
        assertThat(savedOrganization.getValue().getName()).isEqualTo("Acme School");
        assertThat(savedOrganization.getValue().getFacilityType()).isEqualTo(FacilityType.MAIN);
        assertThat(savedOrganization.getValue().getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(savedOrganization.getValue().getTaxCode()).isEqualTo("0123456789");
    }

    @Test
    void onboard_duplicateName_throwsWithoutSaving() {
        OnboardTenantRequest request = new OnboardTenantRequest("acme-school", "Acme School", "SCHOOL",
                "0123456789", "starter", 500);
        when(organizationRepository.existsByNameAndStatusNot("Acme School", OrganizationStatus.REJECTED)).thenReturn(true);

        assertThatThrownBy(() -> service.onboard(request)).isInstanceOf(TenantNameAlreadyUsedException.class);
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void onboard_duplicateTaxCode_throwsWithoutSaving() {
        OnboardTenantRequest request = new OnboardTenantRequest("new-school", "New School", "SCHOOL",
                "0123456789", "starter", 500);
        when(organizationRepository.existsByTaxCodeAndStatusInAndPublicIdNot(eq("0123456789"), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.onboard(request)).isInstanceOf(TenantTaxCodeAlreadyUsedException.class);
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void suspend_activeTenant_transitions() {
        UUID publicId = UUID.randomUUID();
        Organization tenant = activeTenant(publicId);
        when(organizationRepository.findByPublicId(publicId)).thenReturn(Optional.of(tenant));

        OrganizationResponse response = service.suspend(publicId);

        assertThat(response.status()).isEqualTo("SUSPENDED");
    }

    @Test
    void suspend_withRequestedDays_setsBoundedEnd() {
        UUID publicId = UUID.randomUUID();
        Organization tenant = activeTenant(publicId);
        Instant now = Instant.parse("2026-10-17T00:00:00Z");
        service = new OrganizationLifecycleService(organizationRepository, null, () -> 30, null,
                Clock.fixed(now, ZoneOffset.UTC));
        when(organizationRepository.findByPublicId(publicId)).thenReturn(Optional.of(tenant));

        OrganizationResponse response = service.suspend(publicId, 7);

        assertThat(response.status()).isEqualTo("SUSPENDED");
        assertThat(response.suspendedUntil()).isEqualTo(now.plusSeconds(7 * 86_400L));
    }

    @Test
    void suspend_withoutRequestedDays_usesPlatformDefault() {
        UUID publicId = UUID.randomUUID();
        Organization tenant = activeTenant(publicId);
        Instant now = Instant.parse("2026-10-17T00:00:00Z");
        service = new OrganizationLifecycleService(organizationRepository, null, () -> 3, null,
                Clock.fixed(now, ZoneOffset.UTC));
        when(organizationRepository.findByPublicId(publicId)).thenReturn(Optional.of(tenant));

        OrganizationResponse response = service.suspend(publicId);

        assertThat(response.suspendedUntil()).isEqualTo(now.plusSeconds(3 * 86_400L));
    }

    @Test
    void suspend_withZeroDays_isIndefinite() {
        UUID publicId = UUID.randomUUID();
        Organization tenant = activeTenant(publicId);
        when(organizationRepository.findByPublicId(publicId)).thenReturn(Optional.of(tenant));

        OrganizationResponse response = service.suspend(publicId, 0);

        assertThat(response.suspendedUntil()).isNull();
    }

    @Test
    void suspend_withNegativeDays_isRejectedBeforeStateChange() {
        UUID publicId = UUID.randomUUID();
        Organization tenant = activeTenant(publicId);
        when(organizationRepository.findByPublicId(publicId)).thenReturn(Optional.of(tenant));

        assertThatThrownBy(() -> service.suspend(publicId, -1))
                .isInstanceOf(TenantSuspensionDaysInvalidException.class);
        assertThat(tenant.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
    }

    @Test
    void suspensionImpact_delegatesWithResolvedWindow() {
        UUID publicId = UUID.randomUUID();
        Organization tenant = activeTenant(publicId);
        Instant now = Instant.parse("2026-10-17T00:00:00Z");
        TenantSuspensionImpact expected = new TenantSuspensionImpact(publicId, 5,
                now.plusSeconds(5 * 86_400L), 2, 7, List.of());
        service = new OrganizationLifecycleService(organizationRepository, null, () -> 30,
                suspensionImpactProvider, Clock.fixed(now, ZoneOffset.UTC));
        when(organizationRepository.findByPublicId(publicId)).thenReturn(Optional.of(tenant));
        when(suspensionImpactProvider.preview(publicId, now, expected.suspendedUntil())).thenReturn(expected);

        assertThat(service.suspensionImpact(publicId, 5)).isEqualTo(expected);
        verify(suspensionImpactProvider).preview(publicId, now, expected.suspendedUntil());
    }

    @Test
    void platformManagerCanSuspendTenant() {
        UUID publicId = UUID.randomUUID();
        Organization tenant = activeTenant(publicId);
        when(organizationRepository.findByPublicId(publicId)).thenReturn(Optional.of(tenant));

        OrganizationResponse response = service.suspend(publicId,
                new com.pte.shared.security.CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_MANAGER")));

        assertThat(response.status()).isEqualTo("SUSPENDED");
    }

    @Test
    void suspend_alreadySuspended_isIdempotentNoOp() {
        UUID publicId = UUID.randomUUID();
        Organization tenant = activeTenant(publicId);
        tenant.setStatus(OrganizationStatus.SUSPENDED);
        when(organizationRepository.findByPublicId(publicId)).thenReturn(Optional.of(tenant));

        OrganizationResponse response = service.suspend(publicId);

        assertThat(response.status()).isEqualTo("SUSPENDED");
    }

    @Test
    void suspendWithConfirmation_rejectsStaleImpactBeforeStateChange() {
        UUID publicId = UUID.randomUUID();
        Organization tenant = activeTenant(publicId);
        Instant now = Instant.parse("2026-10-17T00:00:00Z");
        TenantSuspensionImpact impact = new TenantSuspensionImpact(publicId, 7,
                now.plusSeconds(7 * 86_400L), 1, 7, List.of(), 7 * 86_400L, 0, "live-digest");
        service = new OrganizationLifecycleService(organizationRepository, null, () -> 30,
                suspensionImpactProvider, Clock.fixed(now, ZoneOffset.UTC));
        when(organizationRepository.findWithLockByPublicId(publicId)).thenReturn(Optional.of(tenant));
        when(suspensionImpactProvider.preview(publicId, now, impact.suspendedUntil())).thenReturn(impact);

        assertThatThrownBy(() -> service.suspendWithConfirmation(publicId,
                new SuspendTenantRequest(7, "stale-digest"), null))
                .isInstanceOf(TenantSuspensionImpactStaleException.class);
        assertThat(tenant.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
    }

    @Test
    void suspendWithConfirmation_appliesMatchingImpactAndUsesTenantLock() {
        UUID publicId = UUID.randomUUID();
        Organization tenant = activeTenant(publicId);
        Instant now = Instant.parse("2026-10-17T00:00:00Z");
        TenantSuspensionImpact impact = new TenantSuspensionImpact(publicId, 7,
                now.plusSeconds(7 * 86_400L), 1, 7, List.of(), 7 * 86_400L, 0, "live-digest");
        service = new OrganizationLifecycleService(organizationRepository, null, () -> 30,
                suspensionImpactProvider, Clock.fixed(now, ZoneOffset.UTC));
        when(organizationRepository.findWithLockByPublicId(publicId)).thenReturn(Optional.of(tenant));
        when(suspensionImpactProvider.preview(publicId, now, impact.suspendedUntil())).thenReturn(impact);

        OrganizationResponse response = service.suspendWithConfirmation(publicId,
                new SuspendTenantRequest(7, "live-digest"), null);

        assertThat(response.status()).isEqualTo("SUSPENDED");
        assertThat(response.suspendedUntil()).isEqualTo(impact.suspendedUntil());
        verify(organizationRepository).findWithLockByPublicId(publicId);
    }

    @Test
    void suspend_unknownTenant_throwsNotFound() {
        UUID publicId = UUID.randomUUID();
        when(organizationRepository.findByPublicId(publicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suspend(publicId)).isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void reactivate_suspendedTenant_transitions() {
        UUID publicId = UUID.randomUUID();
        Organization tenant = activeTenant(publicId);
        tenant.setStatus(OrganizationStatus.SUSPENDED);
        when(organizationRepository.findByPublicId(publicId)).thenReturn(Optional.of(tenant));

        OrganizationResponse response = service.reactivate(publicId);

        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void reactivate_alreadyActive_isIdempotentNoOp() {
        UUID publicId = UUID.randomUUID();
        Organization tenant = activeTenant(publicId);
        when(organizationRepository.findByPublicId(publicId)).thenReturn(Optional.of(tenant));

        OrganizationResponse response = service.reactivate(publicId);

        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void reactivate_unknownTenant_throwsNotFound() {
        UUID publicId = UUID.randomUUID();
        when(organizationRepository.findByPublicId(publicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reactivate(publicId)).isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void updateBranding_setsFields() {
        UUID publicId = UUID.randomUUID();
        Organization tenant = activeTenant(publicId);
        when(organizationRepository.findByPublicId(publicId)).thenReturn(Optional.of(tenant));

        OrganizationResponse response = service.updateBranding(publicId,
                new UpdateBrandingRequest("https://cdn.example.com/logo.png", "#1A2B3C"));

        assertThat(response.logoUrl()).isEqualTo("https://cdn.example.com/logo.png");
        assertThat(response.primaryColor()).isEqualTo("#1A2B3C");
    }

    @Test
    void updateBranding_unknownTenant_throwsNotFound() {
        UUID publicId = UUID.randomUUID();
        when(organizationRepository.findByPublicId(publicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateBranding(publicId, new UpdateBrandingRequest(null, null)))
                .isInstanceOf(TenantNotFoundException.class);
    }
}
