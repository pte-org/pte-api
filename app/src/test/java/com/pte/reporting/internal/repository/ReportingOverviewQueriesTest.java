package com.pte.reporting.internal.repository;

import com.pte.reporting.ReportingOverviewService;
import com.pte.reporting.domain.AttemptReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Unpublished-report count behind {@link ReportingOverviewService}: published flag, soft delete, tenant isolation. */
@DataJpaTest
@Import(ReportingOverviewService.class)
class ReportingOverviewQueriesTest {

    @Autowired
    private ReportingOverviewService service;

    @Autowired
    private AttemptReportRepository reports;

    private void report(UUID tenant, boolean published, boolean deleted) {
        AttemptReport report = new AttemptReport();
        report.setTenantId(tenant);
        report.setStudentPublicId(UUID.randomUUID());
        report.setAttemptPublicId(UUID.randomUUID());
        report.setSessionPublicId(UUID.randomUUID());
        report.setPublished(published);
        report.setPublishedAt(published ? Instant.parse("2026-10-01T00:00:00Z") : null);
        report.setDeleted(deleted);
        reports.saveAndFlush(report);
    }

    @Test
    void countsOnlyUnpublishedReports() {
        UUID tenant = UUID.randomUUID();
        report(tenant, false, false);
        report(tenant, false, false);
        report(tenant, true, false);
        report(tenant, true, false);
        report(tenant, true, false);

        assertThat(service.countAwaitingPublication(tenant)).isEqualTo(2);
    }

    @Test
    void softDeletedReportsAreNotCounted() {
        UUID tenant = UUID.randomUUID();
        report(tenant, false, false);
        report(tenant, false, true);
        report(tenant, false, true);

        assertThat(service.countAwaitingPublication(tenant)).isEqualTo(1);
    }

    @Test
    void anotherTenantsReportsNeverLeakIntoTheCount() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        report(tenantA, false, false);
        for (int i = 0; i < 6; i++) {
            report(tenantB, false, false);
        }

        assertThat(service.countAwaitingPublication(tenantA)).isEqualTo(1);
        assertThat(service.countAwaitingPublication(tenantB)).isEqualTo(6);
    }

    @Test
    void aTenantWithNoReportsHasNothingAwaitingPublication() {
        report(UUID.randomUUID(), false, false);

        assertThat(service.countAwaitingPublication(UUID.randomUUID())).isZero();
    }

    @Test
    void aTenantWhoseReportsAreAllPublishedHasNothingAwaitingPublication() {
        UUID tenant = UUID.randomUUID();
        report(tenant, true, false);
        report(tenant, true, false);

        assertThat(service.countAwaitingPublication(tenant)).isZero();
    }
}
