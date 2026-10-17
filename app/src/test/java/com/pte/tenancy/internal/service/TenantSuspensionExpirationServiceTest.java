package com.pte.tenancy.internal.service;

import com.pte.tenancy.domain.Tenant;
import com.pte.tenancy.domain.enums.TenantStatus;
import com.pte.tenancy.internal.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantSuspensionExpirationServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Test
    void reactivateDueTenants_reactivatesExpiredBoundedSuspensionsOnly() {
        Instant now = Instant.parse("2026-10-17T00:00:00Z");
        Tenant due = suspendedTenant(now.minusSeconds(1), TenantStatus.SUSPENDED);
        Tenant indefinite = suspendedTenant(null, TenantStatus.SUSPENDED);
        when(tenantRepository
                .findByStatusAndSuspendedUntilIsNotNullAndSuspendedUntilLessThanEqualAndDeletedFalse(
                        TenantStatus.SUSPENDED, now))
                .thenReturn(List.of(due));

        TenantSuspensionExpirationService service = new TenantSuspensionExpirationService(
                tenantRepository, Clock.fixed(now, ZoneOffset.UTC));

        assertThat(service.reactivateDueTenants()).isEqualTo(1);
        assertThat(due.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(due.getSuspendedUntil()).isNull();
        assertThat(indefinite.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        verify(tenantRepository)
                .findByStatusAndSuspendedUntilIsNotNullAndSuspendedUntilLessThanEqualAndDeletedFalse(
                        TenantStatus.SUSPENDED, now);
    }

    private Tenant suspendedTenant(Instant suspendedUntil, TenantStatus status) {
        Tenant tenant = new Tenant();
        tenant.setPublicId(UUID.randomUUID());
        tenant.setStatus(status);
        tenant.setSuspendedUntil(suspendedUntil);
        return tenant;
    }
}
