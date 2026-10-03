package com.pte.support.internal.mapper;

import com.pte.support.domain.SupportTicket;
import com.pte.support.domain.SupportTicketNote;
import com.pte.support.internal.dto.response.SupportTicketNoteResponse;
import com.pte.support.internal.dto.response.SupportTicketResponse;
import com.pte.support.internal.dto.response.SupportTicketSummaryResponse;

import java.util.List;

public final class SupportTicketMapper {

    private SupportTicketMapper() {
    }

    public static SupportTicketResponse toResponse(SupportTicket ticket, List<SupportTicketNote> notes) {
        return new SupportTicketResponse(
                ticket.getPublicId(),
                ticket.getTenantId(),
                ticket.getSubmitterUserPublicId(),
                ticket.getCategory(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getEntityType(),
                ticket.getEntityId(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                notes.stream().map(SupportTicketMapper::toNoteResponse).toList()
        );
    }

    public static SupportTicketSummaryResponse toSummaryResponse(SupportTicket ticket) {
        return new SupportTicketSummaryResponse(
                ticket.getPublicId(),
                ticket.getCategory(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getEntityType(),
                ticket.getEntityId(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }

    public static SupportTicketNoteResponse toNoteResponse(SupportTicketNote note) {
        return new SupportTicketNoteResponse(
                note.getPublicId(),
                note.getAdminPublicId(),
                note.getContent(),
                note.getCreatedAt()
        );
    }
}
