package com.pte.common.web;

import java.time.Instant;
import java.util.UUID;

/**
 * Encodes/decodes the {@code (updatedAt, publicId)} keyset cursor used by
 * every {@code /internal/**\/export?since=...} endpoint (rabbitmq-outbox-migration
 * Phase 9) — identical shape across every producing service. Every persistent
 * aggregate root already carries both fields via {@link com.pte.common.domain.BaseEntity},
 * so no service needs a bespoke cursor type.
 */
public final class KeysetCursor {

    private static final String SEPARATOR = "_";

    private KeysetCursor() {
    }

    public static String encode(Instant updatedAt, UUID publicId) {
        return updatedAt.toString() + SEPARATOR + publicId;
    }

    /** Returns {@code null} for a blank/absent cursor — callers treat that as "start from the beginning". */
    public static Cursor decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int separatorIndex = raw.lastIndexOf(SEPARATOR);
        if (separatorIndex < 0) {
            throw new IllegalArgumentException("Malformed export cursor: " + raw);
        }
        Instant updatedAt = Instant.parse(raw.substring(0, separatorIndex));
        UUID publicId = UUID.fromString(raw.substring(separatorIndex + 1));
        return new Cursor(updatedAt, publicId);
    }

    public record Cursor(Instant updatedAt, UUID publicId) {
    }
}
