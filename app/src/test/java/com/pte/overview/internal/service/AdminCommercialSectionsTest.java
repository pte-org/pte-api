package com.pte.overview.internal.service;

import com.pte.billing.BillingOverviewService;
import com.pte.overview.internal.AdminDashboardFixtures;
import com.pte.overview.internal.dto.response.AdminSectionData.Applications;
import com.pte.overview.internal.dto.response.AdminSectionData.Licenses;
import com.pte.overview.internal.dto.response.AdminSectionData.OldestTicket;
import com.pte.overview.internal.dto.response.AdminSectionData.PendingApplication;
import com.pte.overview.internal.dto.response.AdminSectionData.Plans;
import com.pte.overview.internal.dto.response.AdminSectionData.Subscriptions;
import com.pte.overview.internal.dto.response.AdminSectionData.Support;
import com.pte.overview.internal.dto.response.TrendValue;
import com.pte.support.SupportOverviewService.OpenTicketBrief;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.pte.overview.internal.AdminDashboardFixtures.APP_1;
import static com.pte.overview.internal.AdminDashboardFixtures.APP_2;
import static com.pte.overview.internal.AdminDashboardFixtures.NOW;
import static com.pte.overview.internal.AdminDashboardFixtures.T1;
import static com.pte.overview.internal.AdminDashboardFixtures.TICKET_1;
import static com.pte.overview.internal.AdminDashboardFixtures.TICKET_2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Payload composition of the applications, licenses, subscriptions, plans and support admin sections. */
class AdminCommercialSectionsTest {

    private AdminDashboardFixtures fx;
    private AdminCommercialSections sections;
    private DashboardWindow window;

    @BeforeEach
    void setUp() {
        fx = new AdminDashboardFixtures().stubHappyPath();
        sections = fx.commercialSections();
        window = DashboardWindow.of(30, NOW);
    }

    // ---- applications ----

    @Test
    void applicationsCarryThePendingCountTheReceivedTrendAndTheOldestFirstList() {
        Applications applications = sections.applications(window);

        assertThat(applications.pending()).isEqualTo(7);
        assertThat(applications.receivedInWindow()).isEqualTo(new TrendValue(5, 10, -50.0));
        assertThat(applications.oldestPending()).containsExactly(
                new PendingApplication(APP_1, "Org One", "SCHOOL", "ORG1", Instant.parse("2026-09-01T00:00:00Z")),
                new PendingApplication(APP_2, "Org Two", "CENTER", "ORG2", Instant.parse("2026-09-02T00:00:00Z")));
        verify(fx.billing).oldestPendingApplications(5);
    }

    @Test
    void anApplicationRowExposesNoContactTaxCodeOrReviewField() {
        assertThat(componentNames(PendingApplication.class)).containsExactly("applicationId", "orgName", "orgType",
                "requestedCode", "receivedAt");
        assertThat(componentNames(BillingOverviewService.PendingApplicationView.class)).containsExactly("publicId",
                "orgName", "orgType", "requestedCode", "receivedAt");
    }

    // ---- licenses ----

    @Test
    void licensesAreCountsOnlyWithTheDerivedExpiredCountAndTheSevenDayThreshold() {
        Licenses licenses = sections.licenses(window);

        assertThat(licenses.issuedValid()).isEqualTo(40);
        assertThat(licenses.redeemed()).isEqualTo(15);
        assertThat(licenses.revoked()).isEqualTo(3);
        assertThat(licenses.expired()).isEqualTo(9);
        assertThat(licenses.expiringSoon()).isEqualTo(6);
        assertThat(licenses.expiringSoonDays()).isEqualTo(7).isEqualTo(BillingOverviewService.LICENSE_EXPIRING_SOON_DAYS);
        assertThat(licenses.redeemedInWindow()).isEqualTo(new TrendValue(8, 8, 0.0));
        verify(fx.billing).licenseCounts(window.end());
    }

    @Test
    void theLicenseSectionRecordHasNoStringFieldSoNoCodeValueCanLeak() {
        assertThat(Arrays.stream(Licenses.class.getRecordComponents()).map(RecordComponent::getType))
                .doesNotContain(String.class);
        assertThat(componentNames(Licenses.class)).noneMatch(name -> name.toLowerCase().contains("code"));
    }

    // ---- subscriptions ----

    @Test
    void subscriptionsCarryActiveExpiringSoonThirtyDaysAndTheSourceSplit() {
        Subscriptions subscriptions = sections.subscriptions(window);

        assertThat(subscriptions.active()).isEqualTo(33);
        assertThat(subscriptions.expiringSoon()).isEqualTo(5);
        assertThat(subscriptions.expiringSoonDays()).isEqualTo(30).isEqualTo(BillingOverviewService.EXPIRING_SOON_DAYS);
        assertThat(subscriptions.activatedBySource()).containsEntry("PAYMENT", 4L).containsEntry("LICENSE_CODE", 3L);
    }

    @Test
    void theActivationTrendSumsTheSourcesOfEachWindow() {
        // current 4 + 3 = 7, previous 1 + 1 = 2 -> +250%
        assertThat(sections.subscriptions(window).activatedInWindow()).isEqualTo(new TrendValue(7, 2, 250.0));
    }

    // ---- plans ----

    @Test
    void plansCarryTheStatusCountsAndEveryTypeIncludingZeroes() {
        Plans plans = sections.plans();

        assertThat(plans.active()).isEqualTo(4);
        assertThat(plans.draft()).isEqualTo(2);
        assertThat(plans.archived()).isEqualTo(1);
        assertThat(plans.byType()).containsEntry("EXAM_PACKAGE", 5L).containsEntry("STUDENT_CAPACITY", 2L);
    }

    // ---- support ----

    @Test
    void supportCarriesOpenInProgressTheOpenedTrendAndTheOldestOpenTicketsWithTenantNames() {
        Support support = sections.support(window);

        assertThat(support.open()).isEqualTo(11);
        assertThat(support.inProgress()).isEqualTo(4);
        assertThat(support.openedInWindow()).isEqualTo(new TrendValue(9, 3, 200.0));
        assertThat(support.oldestOpen()).containsExactly(
                new OldestTicket(TICKET_1, T1, "Name of " + T1, "BUG", Instant.parse("2026-09-10T00:00:00Z")),
                new OldestTicket(TICKET_2, T1, "Name of " + T1, "CONTENT_COMPLAINT",
                        Instant.parse("2026-09-11T00:00:00Z")));
        verify(fx.support).oldestOpenTickets(5);
    }

    @Test
    void anUnresolvableTenantLeavesTheTicketRowWithANullTenantNameInsteadOfFailing() {
        when(fx.tenancy.findTenantSummaries(anyCollection())).thenReturn(Map.of());

        Support support = sections.support(window);

        assertThat(support.oldestOpen()).hasSize(2).allSatisfy(ticket -> {
            assertThat(ticket.tenantName()).isNull();
            assertThat(ticket.tenantId()).isEqualTo(T1);
        });
    }

    @Test
    void noOpenTicketsMeansAnEmptyList() {
        when(fx.support.oldestOpenTickets(5)).thenReturn(List.of());

        assertThat(sections.support(window).oldestOpen()).isEmpty();
    }

    @Test
    void theTicketRowExposesIdsCategoryAndAgeOnlyNeverTheDescriptionOrTheSubmitter() {
        assertThat(componentNames(OldestTicket.class)).containsExactly("ticketId", "tenantId", "tenantName", "category",
                "openedAt");
        assertThat(componentNames(OpenTicketBrief.class)).containsExactly("publicId", "tenantId", "category", "openedAt");
    }

    @Test
    void supportAsksForOldestOpenTicketsNotInProgressOnesAndDoesNotReadTheDescriptionAtAll() {
        sections.support(window);

        verify(fx.support).platformTicketCounts();
        verify(fx.support).oldestOpenTickets(5);
        verify(fx.support).countOpenedBetween(window.start(), window.end());
        verify(fx.support).countOpenedBetween(window.previousStart(), window.start());
        verifyNoMoreInteractions(fx.support);
    }

    private static List<String> componentNames(Class<?> record) {
        return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
    }
}
