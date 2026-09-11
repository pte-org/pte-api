package com.pte.admin.mapper;

import com.pte.admin.domain.Tenant;
import com.pte.admin.domain.enums.TenantStatus;
import com.pte.admin.dto.response.TenantResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * `GET /tenants` (list) maps one {@link Tenant} per row through this mapper.
 * {@code organizations} is a lazy collection — if the mapper ever touched it,
 * every row would trigger its own SELECT (N+1). This test fails loudly if a
 * future edit adds a call to {@code tenant.getOrganizations()} here.
 */
class TenantMapperTest {

    @Test
    void toResponse_neverTouchesLazyOrganizationsCollection() {
        Tenant tenant = spy(new Tenant());
        tenant.setPublicId(UUID.randomUUID());
        tenant.setName("Acme School");
        tenant.setOrganizationType("SCHOOL");
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setPackageName("starter");
        tenant.setStudentLimit(500);

        TenantResponse response = TenantMapper.toResponse(tenant);

        assertThat(response.name()).isEqualTo("Acme School");
        verify(tenant, never()).getOrganizations();
    }

    @Test
    void toResponse_includesBrandingFields() {
        Tenant tenant = new Tenant();
        tenant.setPublicId(UUID.randomUUID());
        tenant.setName("Acme School");
        tenant.setOrganizationType("SCHOOL");
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setPackageName("starter");
        tenant.setStudentLimit(500);
        tenant.updateBranding("https://cdn.example.com/logo.png", "#1A2B3C");

        TenantResponse response = TenantMapper.toResponse(tenant);

        assertThat(response.logoUrl()).isEqualTo("https://cdn.example.com/logo.png");
        assertThat(response.primaryColor()).isEqualTo("#1A2B3C");
    }
}
