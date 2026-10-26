package com.pte.tenancy.internal.service;

import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import com.pte.shared.audit.AuditLogService;
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
    private OrganizationRepository organizationRepository;

    @Mock
    private AuditLogService auditLogService;

    @Test
    void reactivateDueTenants_reactivatesExpiredBoundedSuspensionsOnly() {
        Instant now = Instant.parse("2026-10-17T00:00:00Z");
        Organization due = suspendedTenant(now.minusSeconds(1), OrganizationStatus.SUSPENDED);
        Organization indefinite = suspendedTenant(null, OrganizationStatus.SUSPENDED);
        when(organizationRepository
                .findByStatusAndSuspendedUntilIsNotNullAndSuspendedUntilLessThanEqualAndDeletedFalse(
                        OrganizationStatus.SUSPENDED, now))
                .thenReturn(List.of(due));

        TenantSuspensionExpirationService service = new TenantSuspensionExpirationService(
                organizationRepository, Clock.fixed(now, ZoneOffset.UTC));

        assertThat(service.reactivateDueTenants()).isEqualTo(1);
        assertThat(due.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(due.getSuspendedUntil()).isNull();
        assertThat(indefinite.getStatus()).isEqualTo(OrganizationStatus.SUSPENDED);
        verify(organizationRepository)
                .findByStatusAndSuspendedUntilIsNotNullAndSuspendedUntilLessThanEqualAndDeletedFalse(
                        OrganizationStatus.SUSPENDED, now);
    }

    @Test
    void reactivateDueTenants_recordsSystemAuditAndIsIdempotentOnNextRun() {
        Instant now = Instant.parse("2026-10-17T00:00:00Z");
        Organization due = suspendedTenant(now.minusSeconds(1), OrganizationStatus.SUSPENDED);
        when(organizationRepository
                .findByStatusAndSuspendedUntilIsNotNullAndSuspendedUntilLessThanEqualAndDeletedFalse(
                        OrganizationStatus.SUSPENDED, now))
                .thenReturn(List.of(due), List.of());

        TenantSuspensionExpirationService service = new TenantSuspensionExpirationService(
                organizationRepository, Clock.fixed(now, ZoneOffset.UTC), auditLogService);

        assertThat(service.reactivateDueTenants()).isEqualTo(1);
        assertThat(service.reactivateDueTenants()).isZero();
        verify(auditLogService).record(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("Tenant"),
                org.mockito.ArgumentMatchers.eq(due.getPublicId().toString()),
                org.mockito.ArgumentMatchers.eq("TenantReactivated"),
                org.mockito.ArgumentMatchers.eq("Reactivated tenant"));
    }

    private Organization suspendedTenant(Instant suspendedUntil, OrganizationStatus status) {
        Organization tenant = new Organization();
        tenant.setPublicId(UUID.randomUUID());
        tenant.setStatus(status);
        tenant.setSuspendedUntil(suspendedUntil);
        return tenant;
    }
}
