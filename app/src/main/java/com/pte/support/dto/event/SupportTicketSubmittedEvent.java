package com.pte.support.dto.event;

import com.pte.support.domain.enums.TicketCategory;

import java.util.UUID;

/** Published after a support ticket is persisted in the caller transaction. */
public record SupportTicketSubmittedEvent(UUID ticketPublicId, UUID tenantId,
        UUID submitterUserPublicId, TicketCategory category) {
}
