package com.pte.session;

import java.time.Instant;
import java.util.UUID;

/**
 * Dashboard read model of a joinable session. {@code status} is the session
 * lifecycle name (a string so no domain enum leaks across the module boundary);
 * {@code capacity} may be null for legacy rows.
 */
public record UpcomingSessionView(UUID publicId, String name, String status, Instant opensAt, Instant closesAt,
        long enrolled, Integer capacity) {
}
