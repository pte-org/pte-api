package com.pte.session.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Minimal, server-owned schedule view used by the notification reminder worker. */
public record ClosingSoonSessionView(UUID sessionPublicId, UUID tenantId, String name,
        Instant opensAt, Instant closesAt) {
}
