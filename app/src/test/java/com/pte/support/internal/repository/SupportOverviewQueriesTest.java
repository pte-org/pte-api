package com.pte.support.internal.repository;

import com.pte.support.SupportOverviewService;
import com.pte.support.SupportOverviewService.TicketCounts;
import com.pte.support.domain.SupportTicket;
import com.pte.support.domain.enums.TicketCategory;
import com.pte.support.domain.enums.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Ticket counts behind {@link SupportOverviewService}: status mapping, soft delete, tenant isolation. */
@DataJpaTest
@Import(SupportOverviewService.class)
class SupportOverviewQueriesTest {

    @Autowired
    private SupportOverviewService service;

    @Autowired
    private SupportTicketRepository tickets;

    private void ticket(UUID tenant, TicketStatus status, boolean deleted) {
        SupportTicket ticket = new SupportTicket();
        ticket.setTenantId(tenant);
        ticket.setSubmitterUserPublicId(UUID.randomUUID());
        ticket.setCategory(TicketCategory.BUG);
        ticket.setDescription("Overview test ticket");
        ticket.setStatus(status);
        ticket.setDeleted(deleted);
        tickets.saveAndFlush(ticket);
    }

    private void tickets(UUID tenant, TicketStatus status, int count) {
        for (int i = 0; i < count; i++) {
            ticket(tenant, status, false);
        }
    }

    @Test
    void countsPerStatusMapToOpenInProgressAndResolvedAwaitingClose() {
        UUID tenant = UUID.randomUUID();
        tickets(tenant, TicketStatus.OPEN, 3);
        tickets(tenant, TicketStatus.IN_PROGRESS, 2);
        tickets(tenant, TicketStatus.RESOLVED, 4);

        assertThat(service.ticketCounts(tenant)).isEqualTo(new TicketCounts(3, 2, 4));
    }

    @Test
    void closedTicketsAreNotPartOfAnyCounter() {
        UUID tenant = UUID.randomUUID();
        tickets(tenant, TicketStatus.CLOSED, 7);
        tickets(tenant, TicketStatus.OPEN, 1);

        assertThat(service.ticketCounts(tenant)).isEqualTo(new TicketCounts(1, 0, 0));
    }

    @Test
    void missingStatusesAreZero() {
        UUID tenant = UUID.randomUUID();
        tickets(tenant, TicketStatus.IN_PROGRESS, 1);

        assertThat(service.ticketCounts(tenant)).isEqualTo(new TicketCounts(0, 1, 0));
    }

    @Test
    void aTenantWithNoTicketsIsAllZeros() {
        tickets(UUID.randomUUID(), TicketStatus.OPEN, 3);

        assertThat(service.ticketCounts(UUID.randomUUID())).isEqualTo(new TicketCounts(0, 0, 0));
    }

    @Test
    void softDeletedTicketsAreNotCounted() {
        UUID tenant = UUID.randomUUID();
        ticket(tenant, TicketStatus.OPEN, false);
        ticket(tenant, TicketStatus.OPEN, true);
        ticket(tenant, TicketStatus.IN_PROGRESS, true);
        ticket(tenant, TicketStatus.RESOLVED, true);

        assertThat(service.ticketCounts(tenant)).isEqualTo(new TicketCounts(1, 0, 0));
    }

    @Test
    void tenantAIsNeverCountedWithTenantBTickets() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        tickets(tenantA, TicketStatus.OPEN, 1);
        tickets(tenantA, TicketStatus.RESOLVED, 1);
        tickets(tenantB, TicketStatus.OPEN, 50);
        tickets(tenantB, TicketStatus.IN_PROGRESS, 60);
        tickets(tenantB, TicketStatus.RESOLVED, 70);

        assertThat(service.ticketCounts(tenantA)).isEqualTo(new TicketCounts(1, 0, 1));
        assertThat(service.ticketCounts(tenantB)).isEqualTo(new TicketCounts(50, 60, 70));
    }
}
