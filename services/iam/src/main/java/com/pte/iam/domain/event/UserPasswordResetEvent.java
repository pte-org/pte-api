package com.pte.iam.domain.event;

import java.util.UUID;

/** Payload for the {@code UserPasswordReset} outbox event. Never carries password material. */
public record UserPasswordResetEvent(UUID userPublicId, UUID resetByUserId, UUID tenantId) {
}
