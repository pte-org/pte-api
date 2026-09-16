package com.pte.tenancy.internal.service;

import com.pte.tenancy.internal.constant.TenancyConstants;
import com.pte.tenancy.domain.Tenant;
import com.pte.tenancy.domain.enums.TenantStatus;
import com.pte.tenancy.internal.exception.TenantNameAlreadyUsedException;
import com.pte.tenancy.internal.exception.TenantNotFoundException;
import com.pte.tenancy.internal.dto.request.OnboardTenantRequest;
import com.pte.tenancy.internal.dto.request.UpdateBrandingRequest;
import com.pte.tenancy.internal.dto.response.TenantResponse;
import com.pte.tenancy.internal.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantLifecycleServiceTest {

    @Mock
    private TenantRepository tenantRepository;


    private TenantLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new TenantLifecycleService(tenantRepository);
    }

    private Tenant activeTenant(UUID publicId) {
        Tenant tenant = new Tenant();
        tenant.setPublicId(publicId);
        tenant.setName("Acme School");
        tenant.setOrganizationType("SCHOOL");
        tenant.setPackageName("starter");
        tenant.setStudentLimit(500);
        tenant.setStatus(TenantStatus.ACTIVE);
        return tenant;
    }

    @Test
    void onboard_savesTenant() {
        OnboardTenantRequest request = new OnboardTenantRequest("Acme School", "SCHOOL", "starter", 500);
        when(tenantRepository.existsByName("Acme School")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant saved = invocation.getArgument(0);
            saved.setPublicId(UUID.randomUUID());
            return saved;
        });

        TenantResponse response = service.onboard(request);

        assertThat(response.name()).isEqualTo("Acme School");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void onboard_duplicateName_throwsWithoutSaving() {
        OnboardTenantRequest request = new OnboardTenantRequest("Acme School", "SCHOOL", "starter", 500);
        when(tenantRepository.existsByName("Acme School")).thenReturn(true);

        assertThatThrownBy(() -> service.onboard(request)).isInstanceOf(TenantNameAlreadyUsedException.class);
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void suspend_activeTenant_transitions() {
        UUID publicId = UUID.randomUUID();
        Tenant tenant = activeTenant(publicId);
        when(tenantRepository.findByPublicId(publicId)).thenReturn(Optional.of(tenant));

        TenantResponse response = service.suspend(publicId);

        assertThat(response.status()).isEqualTo("SUSPENDED");
    }

    @Test
    void suspend_alreadySuspended_isIdempotentNoOp() {
        UUID publicId = UUID.randomUUID();
        Tenant tenant = activeTenant(publicId);
        tenant.setStatus(TenantStatus.SUSPENDED);
        when(tenantRepository.findByPublicId(publicId)).thenReturn(Optional.of(tenant));

        TenantResponse response = service.suspend(publicId);

        assertThat(response.status()).isEqualTo("SUSPENDED");
    }

    @Test
    void suspend_unknownTenant_throwsNotFound() {
        UUID publicId = UUID.randomUUID();
        when(tenantRepository.findByPublicId(publicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suspend(publicId)).isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void reactivate_suspendedTenant_transitions() {
        UUID publicId = UUID.randomUUID();
        Tenant tenant = activeTenant(publicId);
        tenant.setStatus(TenantStatus.SUSPENDED);
        when(tenantRepository.findByPublicId(publicId)).thenReturn(Optional.of(tenant));

        TenantResponse response = service.reactivate(publicId);

        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void reactivate_alreadyActive_isIdempotentNoOp() {
        UUID publicId = UUID.randomUUID();
        Tenant tenant = activeTenant(publicId);
        when(tenantRepository.findByPublicId(publicId)).thenReturn(Optional.of(tenant));

        TenantResponse response = service.reactivate(publicId);

        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void reactivate_unknownTenant_throwsNotFound() {
        UUID publicId = UUID.randomUUID();
        when(tenantRepository.findByPublicId(publicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reactivate(publicId)).isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void updateBranding_setsFields() {
        UUID publicId = UUID.randomUUID();
        Tenant tenant = activeTenant(publicId);
        when(tenantRepository.findByPublicId(publicId)).thenReturn(Optional.of(tenant));

        TenantResponse response = service.updateBranding(publicId,
                new UpdateBrandingRequest("https://cdn.example.com/logo.png", "#1A2B3C"));

        assertThat(response.logoUrl()).isEqualTo("https://cdn.example.com/logo.png");
        assertThat(response.primaryColor()).isEqualTo("#1A2B3C");
    }

    @Test
    void updateBranding_unknownTenant_throwsNotFound() {
        UUID publicId = UUID.randomUUID();
        when(tenantRepository.findByPublicId(publicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateBranding(publicId, new UpdateBrandingRequest(null, null)))
                .isInstanceOf(TenantNotFoundException.class);
    }
}
