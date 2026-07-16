package com.aptis.common.event;

/**
 * Published on every successful logout. Lives in `common` so both the publisher (iam) and
 * any listener (e.g. proctor's WebSocket session revocation) depend only on this neutral
 * event type, not on each other — avoids an iam↔proctor module cycle.
 */
public record UserLoggedOutEvent(Long userId, String userType) {
}
