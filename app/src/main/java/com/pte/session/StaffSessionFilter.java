package com.pte.session;

import java.time.Instant;
import java.util.UUID;

/** UTC half-open schedule filter passed through a trusted, already-authorized public facade. */
public record StaffSessionFilter(Instant from, Instant to, UUID sessionPublicId, String sessionStatus,
        boolean ascending) {
}
