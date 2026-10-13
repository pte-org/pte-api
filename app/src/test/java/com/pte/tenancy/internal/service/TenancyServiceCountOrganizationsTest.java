package com.pte.tenancy.internal.service;

import com.pte.tenancy.StudentCountProvider;
import com.pte.tenancy.TenancyService;
import com.pte.tenancy.internal.exception.TenantNotFoundException;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import com.pte.tenancy.internal.repository.TenantRepository;
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
    private final TenancyService tenancy = new TenancyService(organizations, mock(TenantRepository.class),
            mock(TenantLifecycleService.class), mock(QuotaTransactionService.class), mock(StudentCountProvider.class));

    @Test
    void returnsTheNonDeletedOrganizationCountOfTheGivenTenant() {
        UUID tenantId = UUID.randomUUID();
        when(organizations.countByTenant_PublicIdAndDeletedFalse(tenantId)).thenReturn(4L);

        assertThat(tenancy.countOrganizations(tenantId)).isEqualTo(4);
        verify(organizations).countByTenant_PublicIdAndDeletedFalse(tenantId);
    }

    @Test
    void aMissingTenantIsRejectedBeforeTheRepositoryIsQueried() {
        assertThatThrownBy(() -> tenancy.countOrganizations(null)).isInstanceOf(TenantNotFoundException.class);

        verifyNoInteractions(organizations);
    }
}
