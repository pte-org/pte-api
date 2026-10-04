package com.pte.support.internal.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SupportTicketNoteResponse(
        UUID publicId,
        UUID adminPublicId,
        String content,
        Instant createdAt
) {
}
