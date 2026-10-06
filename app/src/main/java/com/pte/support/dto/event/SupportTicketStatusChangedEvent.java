package com.pte.support.dto.event;

import com.pte.support.domain.enums.TicketCategory;
import com.pte.support.domain.enums.TicketStatus;

import java.util.UUID;

/** Published only after a valid support-ticket status transition. */
public record SupportTicketStatusChangedEvent(UUID ticketPublicId, UUID tenantId,
        UUID submitterUserPublicId, TicketCategory category,
        TicketStatus previousStatus, TicketStatus newStatus) {
}
