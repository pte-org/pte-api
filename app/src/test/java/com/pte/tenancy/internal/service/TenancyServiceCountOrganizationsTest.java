package com.pte.tenancy.internal.service;

import com.pte.tenancy.StudentCountProvider;
import com.pte.tenancy.TenancyService;
import com.pte.tenancy.internal.exception.TenantNotFoundException;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TenancyServiceCountOrganizationsTest {

    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final TenancyService tenancy = new TenancyService(organizations,
            mock(OrganizationLifecycleService.class), mock(QuotaGrantService.class), mock(StudentCountProvider.class));

    @Test
    void returnsOneWhenTheOrganizationOfTheGivenTenantExists() {
        UUID tenantId = UUID.randomUUID();
        when(organizations.existsByPublicId(tenantId)).thenReturn(true);

        assertThat(tenancy.countOrganizations(tenantId)).isEqualTo(1);
        verify(organizations).existsByPublicId(tenantId);
    }

    @Test
    void returnsZeroWhenTheOrganizationOfTheGivenTenantDoesNotExist() {
        UUID tenantId = UUID.randomUUID();
        when(organizations.existsByPublicId(tenantId)).thenReturn(false);

        assertThat(tenancy.countOrganizations(tenantId)).isZero();
    }

    @Test
    void aMissingTenantIsRejectedBeforeTheRepositoryIsQueried() {
        assertThatThrownBy(() -> tenancy.countOrganizations(null)).isInstanceOf(TenantNotFoundException.class);

        verifyNoInteractions(organizations);
    }
}
