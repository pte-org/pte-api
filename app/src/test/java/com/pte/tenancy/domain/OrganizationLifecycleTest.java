package com.pte.tenancy.domain;

import com.pte.tenancy.domain.enums.OrganizationStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phase 2: Tenant + Organization + TenantApplication collapse into one Organization with a status workflow. */
class OrganizationLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-10-24T00:00:00Z");

    @Test
    void anApplicationStartsPendingAndIsApprovedByAReviewer() {
        Organization organization = new Organization();
        UUID reviewer = UUID.randomUUID();

        assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.PENDING);

        organization.approve(reviewer, NOW);

        assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(organization.getReviewedBy()).isEqualTo(reviewer);
        assertThat(organization.getReviewedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectionKeepsTheReasonAndOnlyAppliesToPendingApplications() {
        Organization organization = new Organization();
        UUID reviewer = UUID.randomUUID();

        organization.reject(reviewer, NOW, "Tax code could not be verified");

        assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.REJECTED);
        assertThat(organization.getRejectReason()).isEqualTo("Tax code could not be verified");
        assertThatThrownBy(() -> organization.approve(reviewer, NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anActiveOrganizationCanBeSuspendedUntilAnInstantAndReactivated() {
        Organization organization = new Organization();
        organization.approve(UUID.randomUUID(), NOW);

        organization.suspendUntil(NOW.plusSeconds(3600));

        assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.SUSPENDED);
        assertThat(organization.getSuspendedUntil()).isEqualTo(NOW.plusSeconds(3600));

        organization.reactivate();

        assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(organization.getSuspendedUntil()).isNull();
    }

    @Test
    void aPendingApplicationCannotBeSuspended() {
        Organization organization = new Organization();

        assertThatThrownBy(() -> organization.suspendUntil(NOW)).isInstanceOf(IllegalStateException.class);
    }
}
