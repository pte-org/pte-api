package com.pte.support.dto.event;

import com.pte.support.domain.enums.TicketCategory;

import java.util.UUID;

/** Published after an administrator note is persisted for a support ticket. */
public record SupportTicketNoteAddedEvent(UUID ticketPublicId, UUID tenantId,
        UUID submitterUserPublicId, UUID notePublicId, TicketCategory category) {
}
