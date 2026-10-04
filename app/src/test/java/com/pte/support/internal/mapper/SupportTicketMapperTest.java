package com.pte.support.internal.mapper;

import com.pte.support.domain.SupportTicket;
import com.pte.support.domain.SupportTicketNote;
import com.pte.support.domain.enums.TicketCategory;
import com.pte.support.domain.enums.TicketEntityType;
import com.pte.support.domain.enums.TicketStatus;
import com.pte.support.internal.dto.response.SupportTicketResponse;
import com.pte.support.internal.dto.response.SupportTicketSummaryResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SupportTicketMapperTest {

    private SupportTicket buildTicket() {
        SupportTicket ticket = new SupportTicket();
        ticket.setPublicId(UUID.randomUUID());
        ticket.setTenantId(UUID.randomUUID());
        ticket.setSubmitterUserPublicId(UUID.randomUUID());
        ticket.setCategory(TicketCategory.BUG);
        ticket.setDescription("Some bug description");
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setEntityType(TicketEntityType.QUESTION);
        ticket.setEntityId(UUID.randomUUID().toString());
        Instant now = Instant.now();
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        return ticket;
    }

    private SupportTicketNote buildNote(UUID ticketPublicId) {
        SupportTicketNote note = new SupportTicketNote();
        note.setPublicId(UUID.randomUUID());
        note.setTicketId(1L);
        note.setTicketPublicId(ticketPublicId);
        note.setAdminPublicId(UUID.randomUUID());
        note.setContent("Admin note");
        note.setCreatedAt(Instant.now());
        return note;
    }

    @Test
    void toResponse_mapsAllFields() {
        SupportTicket ticket = buildTicket();
        SupportTicketNote note = buildNote(ticket.getPublicId());

        SupportTicketResponse response = SupportTicketMapper.toResponse(ticket, List.of(note));

        assertThat(response.publicId()).isEqualTo(ticket.getPublicId());
        assertThat(response.tenantId()).isEqualTo(ticket.getTenantId());
        assertThat(response.submitterUserPublicId()).isEqualTo(ticket.getSubmitterUserPublicId());
        assertThat(response.category()).isEqualTo(TicketCategory.BUG);
        assertThat(response.description()).isEqualTo("Some bug description");
        assertThat(response.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(response.entityType()).isEqualTo(TicketEntityType.QUESTION);
        assertThat(response.entityId()).isEqualTo(ticket.getEntityId());
        assertThat(response.createdAt()).isEqualTo(ticket.getCreatedAt());
        assertThat(response.updatedAt()).isEqualTo(ticket.getUpdatedAt());
        assertThat(response.notes()).hasSize(1);
        assertThat(response.notes().get(0).publicId()).isEqualTo(note.getPublicId());
        assertThat(response.notes().get(0).adminPublicId()).isEqualTo(note.getAdminPublicId());
        assertThat(response.notes().get(0).content()).isEqualTo("Admin note");
        assertThat(response.notes().get(0).createdAt()).isEqualTo(note.getCreatedAt());
    }

    @Test
    void toSummaryResponse_excludesNotes() {
        SupportTicket ticket = buildTicket();

        SupportTicketSummaryResponse response = SupportTicketMapper.toSummaryResponse(ticket);

        assertThat(response.publicId()).isEqualTo(ticket.getPublicId());
        assertThat(response.category()).isEqualTo(TicketCategory.BUG);
        assertThat(response.description()).isEqualTo("Some bug description");
        assertThat(response.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(response.entityType()).isEqualTo(TicketEntityType.QUESTION);
        assertThat(response.entityId()).isEqualTo(ticket.getEntityId());
        assertThat(response.createdAt()).isEqualTo(ticket.getCreatedAt());
        assertThat(response.updatedAt()).isEqualTo(ticket.getUpdatedAt());
        // SupportTicketSummaryResponse is a record with no notes field — the type itself enforces exclusion.
        // Verify via compile-time type: if notes were present this would not compile.
        assertThat(response).isInstanceOf(SupportTicketSummaryResponse.class);
    }
}
