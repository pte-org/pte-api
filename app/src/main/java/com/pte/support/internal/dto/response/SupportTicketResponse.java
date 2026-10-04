package com.pte.support.internal.dto.response;

import com.pte.support.domain.enums.TicketCategory;
import com.pte.support.domain.enums.TicketEntityType;
import com.pte.support.domain.enums.TicketStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SupportTicketResponse(
        UUID publicId,
        UUID tenantId,
        UUID submitterUserPublicId,
        TicketCategory category,
        String description,
        TicketStatus status,
        TicketEntityType entityType,
        String entityId,
        Instant createdAt,
        Instant updatedAt,
        List<SupportTicketNoteResponse> notes
) {
}
