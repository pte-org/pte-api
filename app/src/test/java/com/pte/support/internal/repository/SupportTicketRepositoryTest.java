package com.pte.support.internal.repository;

import com.pte.support.domain.SupportTicket;
import com.pte.support.domain.enums.TicketCategory;
import com.pte.support.domain.enums.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SupportTicketRepositoryTest {

    @Autowired
    private SupportTicketRepository ticketRepository;

    private SupportTicket savedTicket(UUID tenantId) {
        SupportTicket ticket = new SupportTicket();
        ticket.setTenantId(tenantId);
        ticket.setSubmitterUserPublicId(UUID.randomUUID());
        ticket.setCategory(TicketCategory.BUG);
        ticket.setDescription("Test description");
        return ticketRepository.saveAndFlush(ticket);
    }

    @Test
    void findByPublicIdAndTenantId_excludesOtherTenant() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        SupportTicket ticketA = savedTicket(tenantA);
        savedTicket(tenantB);

        Optional<SupportTicket> result = ticketRepository.findByPublicIdAndTenantId(ticketA.getPublicId(), tenantA);
        assertThat(result).isPresent();
        assertThat(result.get().getTenantId()).isEqualTo(tenantA);

        Optional<SupportTicket> wrongTenant = ticketRepository.findByPublicIdAndTenantId(ticketA.getPublicId(), tenantB);
        assertThat(wrongTenant).isEmpty();
    }

    @Test
    void findByPublicId_returnsTicketRegardlessOfTenant() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        SupportTicket ticketA = savedTicket(tenantA);
        SupportTicket ticketB = savedTicket(tenantB);

        Optional<SupportTicket> resultA = ticketRepository.findByPublicId(ticketA.getPublicId());
        assertThat(resultA).isPresent();
        assertThat(resultA.get().getTenantId()).isEqualTo(tenantA);

        Optional<SupportTicket> resultB = ticketRepository.findByPublicId(ticketB.getPublicId());
        assertThat(resultB).isPresent();
        assertThat(resultB.get().getTenantId()).isEqualTo(tenantB);
    }
}
