package com.pte.support.internal.repository;

import com.pte.support.SupportOverviewService;
import com.pte.support.SupportOverviewService.OpenTicketBrief;
import com.pte.support.SupportOverviewService.TicketCounts;
import com.pte.support.domain.SupportTicket;
import com.pte.support.domain.enums.TicketCategory;
import com.pte.support.domain.enums.TicketStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Platform-wide support reads behind {@link SupportOverviewService}: counts across every tenant, opened-in-window
 * ranges, and the oldest OPEN tickets with safe fields only.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(SupportOverviewService.class)
class SupportPlatformOverviewQueriesTest {

    private static final Instant FROM = Instant.parse("2026-09-13T03:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-13T03:00:00Z");

    @Autowired
    private SupportOverviewService service;

    @Autowired
    private SupportTicketRepository tickets;

    @Autowired
    private EntityManager em;

    @Autowired
    private EntityManagerFactory emf;

    private SupportTicket ticket(UUID tenant, TicketStatus status, boolean deleted, Instant createdAt,
            TicketCategory category) {
        SupportTicket ticket = new SupportTicket();
        ticket.setTenantId(tenant);
        ticket.setSubmitterUserPublicId(UUID.randomUUID());
        ticket.setCategory(category);
        ticket.setDescription("PRIVATE-DESCRIPTION " + UUID.randomUUID());
        ticket.setStatus(status);
        ticket.setDeleted(deleted);
        // the entity has a @Version, so Spring Data merges it: only the returned instance carries the ids
        SupportTicket saved = tickets.saveAndFlush(ticket);
        if (createdAt != null) {
            // createdAt is @CreationTimestamp and not updatable: rewrite it with a bulk update.
            em.createQuery("update SupportTicket t set t.createdAt = :c where t.id = :id")
                    .setParameter("c", createdAt).setParameter("id", saved.getId()).executeUpdate();
        }
        em.clear();
        return saved;
    }

    private SupportTicket open(UUID tenant, Instant createdAt) {
        return ticket(tenant, TicketStatus.OPEN, false, createdAt, TicketCategory.BUG);
    }

    // ---- platformTicketCounts ----

    @Test
    void platformCountsSpanEveryTenantAndMapStatusesToOpenInProgressAndResolved() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        ticket(a, TicketStatus.OPEN, false, null, TicketCategory.BUG);
        ticket(a, TicketStatus.OPEN, false, null, TicketCategory.BUG);
        ticket(b, TicketStatus.OPEN, false, null, TicketCategory.GENERAL_FEEDBACK);
        ticket(a, TicketStatus.IN_PROGRESS, false, null, TicketCategory.BUG);
        ticket(b, TicketStatus.IN_PROGRESS, false, null, TicketCategory.BUG);
        ticket(b, TicketStatus.RESOLVED, false, null, TicketCategory.BUG);
        ticket(a, TicketStatus.CLOSED, false, null, TicketCategory.BUG); // closed is not part of any counter

        assertThat(service.platformTicketCounts()).isEqualTo(new TicketCounts(3, 2, 1));
    }

    @Test
    void platformCountsExcludeSoftDeletedTickets() {
        UUID tenant = UUID.randomUUID();
        ticket(tenant, TicketStatus.OPEN, false, null, TicketCategory.BUG);
        ticket(tenant, TicketStatus.OPEN, true, null, TicketCategory.BUG);
        ticket(tenant, TicketStatus.IN_PROGRESS, true, null, TicketCategory.BUG);
        ticket(tenant, TicketStatus.RESOLVED, true, null, TicketCategory.BUG);

        assertThat(service.platformTicketCounts()).isEqualTo(new TicketCounts(1, 0, 0));
    }

    @Test
    void anEmptyPlatformIsAllZerosAndTheTenantScopedCountStaysScoped() {
        UUID mine = UUID.randomUUID();
        assertThat(service.platformTicketCounts()).isEqualTo(new TicketCounts(0, 0, 0));
        ticket(mine, TicketStatus.OPEN, false, null, TicketCategory.BUG);
        ticket(UUID.randomUUID(), TicketStatus.OPEN, false, null, TicketCategory.BUG);

        assertThat(service.ticketCounts(mine)).isEqualTo(new TicketCounts(1, 0, 0));
        assertThat(service.platformTicketCounts()).isEqualTo(new TicketCounts(2, 0, 0));
    }

    // ---- countOpenedBetween ----

    @Test
    void openedInWindowIsHalfOpenCountsEveryStatusAndSkipsDeleted() {
        UUID tenant = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        ticket(tenant, TicketStatus.OPEN, false, FROM, TicketCategory.BUG); // lower bound included
        ticket(other, TicketStatus.CLOSED, false, TO.minusSeconds(1), TicketCategory.BUG); // any status, any tenant
        ticket(tenant, TicketStatus.RESOLVED, false, Instant.parse("2026-09-20T00:00:00Z"), TicketCategory.BUG);
        ticket(tenant, TicketStatus.OPEN, false, TO, TicketCategory.BUG); // upper bound excluded
        ticket(tenant, TicketStatus.OPEN, false, FROM.minusSeconds(1), TicketCategory.BUG); // before
        ticket(tenant, TicketStatus.OPEN, true, Instant.parse("2026-09-20T00:00:00Z"), TicketCategory.BUG); // deleted

        assertThat(service.countOpenedBetween(FROM, TO)).isEqualTo(3);
    }

    @Test
    void adjacentOpenedRangesPartition() {
        Instant middle = Instant.parse("2026-09-28T00:00:00Z");
        open(UUID.randomUUID(), FROM);
        open(UUID.randomUUID(), middle); // exactly on the shared boundary
        open(UUID.randomUUID(), TO.minusSeconds(1));

        long first = service.countOpenedBetween(FROM, middle);
        long second = service.countOpenedBetween(middle, TO);

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(2);
        assertThat(first + second).isEqualTo(service.countOpenedBetween(FROM, TO));
    }

    // ---- oldestOpenTickets ----

    @Test
    void oldestOpenAreOldestFirstAcrossTenantsAndOnlyInTheOpenState() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        SupportTicket newest = open(a, Instant.parse("2026-10-10T00:00:00Z"));
        SupportTicket oldest = open(b, Instant.parse("2026-08-01T00:00:00Z"));
        SupportTicket middle = open(a, Instant.parse("2026-09-01T00:00:00Z"));
        ticket(a, TicketStatus.IN_PROGRESS, false, Instant.parse("2026-07-01T00:00:00Z"), TicketCategory.BUG);
        ticket(a, TicketStatus.RESOLVED, false, Instant.parse("2026-07-02T00:00:00Z"), TicketCategory.BUG);
        ticket(a, TicketStatus.CLOSED, false, Instant.parse("2026-07-03T00:00:00Z"), TicketCategory.BUG);
        ticket(a, TicketStatus.OPEN, true, Instant.parse("2026-06-01T00:00:00Z"), TicketCategory.BUG); // deleted

        List<OpenTicketBrief> top2 = service.oldestOpenTickets(2);
        List<OpenTicketBrief> all = service.oldestOpenTickets(10);

        assertThat(top2).extracting(OpenTicketBrief::publicId).containsExactly(oldest.getPublicId(), middle.getPublicId());
        assertThat(all).extracting(OpenTicketBrief::publicId)
                .containsExactly(oldest.getPublicId(), middle.getPublicId(), newest.getPublicId());
        assertThat(all).extracting(OpenTicketBrief::tenantId).containsExactly(b, a, a);
    }

    @Test
    void theBriefCarriesIdsCategoryNameAndAgeOnly() {
        UUID tenant = UUID.randomUUID();
        SupportTicket saved = ticket(tenant, TicketStatus.OPEN, false, Instant.parse("2026-09-01T00:00:00Z"),
                TicketCategory.CONTENT_COMPLAINT);

        OpenTicketBrief brief = service.oldestOpenTickets(1).get(0);

        assertThat(brief).isEqualTo(new OpenTicketBrief(saved.getPublicId(), tenant, "CONTENT_COMPLAINT",
                Instant.parse("2026-09-01T00:00:00Z")));
        assertThat(brief.toString()).doesNotContain("PRIVATE-DESCRIPTION");
        assertThat(Arrays.stream(OpenTicketBrief.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("publicId", "tenantId", "category", "openedAt");
    }

    @Test
    void ticketsOpenedAtTheSameInstantKeepAStableOrderByInsertion() {
        Instant same = Instant.parse("2026-09-01T00:00:00Z");
        SupportTicket first = open(UUID.randomUUID(), same);
        SupportTicket second = open(UUID.randomUUID(), same);

        assertThat(service.oldestOpenTickets(5)).extracting(OpenTicketBrief::publicId)
                .containsExactly(first.getPublicId(), second.getPublicId());
    }

    @Test
    void aNonPositiveLimitReturnsNothingWithoutQuerying() {
        open(UUID.randomUUID(), null);
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        assertThat(service.oldestOpenTickets(0)).isEmpty();
        assertThat(service.oldestOpenTickets(-5)).isEmpty();
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    @Test
    void theOldestListIsOneBoundedStatementRegardlessOfHowManyTicketsExist() {
        for (int i = 0; i < 12; i++) {
            open(UUID.randomUUID(), Instant.parse("2026-09-01T00:00:00Z").plusSeconds(i));
        }
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        List<OpenTicketBrief> oldest = service.oldestOpenTickets(5);

        assertThat(oldest).hasSize(5);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void noOpenTicketsIsAnEmptyList() {
        ticket(UUID.randomUUID(), TicketStatus.CLOSED, false, null, TicketCategory.BUG);

        assertThat(service.oldestOpenTickets(5)).isEmpty();
    }
}
