package com.pte.support.internal.dto.response;

import com.pte.support.domain.enums.TicketCategory;
import com.pte.support.domain.enums.TicketEntityType;
import com.pte.support.domain.enums.TicketStatus;

import java.time.Instant;
import java.util.UUID;

public record SupportTicketSummaryResponse(
        UUID publicId,
        TicketCategory category,
        String description,
        TicketStatus status,
        TicketEntityType entityType,
        String entityId,
        Instant createdAt,
        Instant updatedAt
) {
}
