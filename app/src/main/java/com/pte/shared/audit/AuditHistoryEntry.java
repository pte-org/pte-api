package com.pte.shared.audit;

import java.time.Instant;
import java.util.UUID;

/** One audited change as read back for a history or receipt view, with the optional before/after JSON states. */
public record AuditHistoryEntry(
        UUID publicId,
        UUID actorUserId,
        String actorRole,
        UUID requestPublicId,
        String summary,
        String beforeStateJson,
        String afterStateJson,
        Instant createdAt) {
}
