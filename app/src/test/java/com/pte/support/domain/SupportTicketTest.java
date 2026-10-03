package com.pte.support.domain;

import com.pte.support.domain.enums.TicketStatus;
import com.pte.support.internal.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportTicketTest {

    private SupportTicket newTicket() {
        SupportTicket ticket = new SupportTicket();
        ticket.setStatus(TicketStatus.OPEN);
        return ticket;
    }

    @Test
    void newTicket_hasStatusOpen() {
        assertThat(new SupportTicket().getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void startProcessing_fromOpen_transitionsToInProgress() {
        SupportTicket ticket = newTicket();
        ticket.startProcessing();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    void startProcessing_fromResolved_throwsInvalidTransition() {
        SupportTicket ticket = newTicket();
        ticket.startProcessing();
        ticket.resolve();
        assertThatThrownBy(ticket::startProcessing)
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void resolve_fromInProgress_transitionsToResolved() {
        SupportTicket ticket = newTicket();
        ticket.startProcessing();
        ticket.resolve();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
    }

    @Test
    void resolve_fromOpen_throwsInvalidTransition() {
        SupportTicket ticket = newTicket();
        assertThatThrownBy(ticket::resolve)
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void resolve_alreadyResolved_throwsInvalidTransition() {
        SupportTicket ticket = newTicket();
        ticket.startProcessing();
        ticket.resolve();
        assertThatThrownBy(ticket::resolve)
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void startProcessing_alreadyInProgress_throwsInvalidTransition() {
        SupportTicket ticket = newTicket();
        ticket.startProcessing();
        assertThatThrownBy(ticket::startProcessing)
                .isInstanceOf(InvalidStatusTransitionException.class);
    }
}
