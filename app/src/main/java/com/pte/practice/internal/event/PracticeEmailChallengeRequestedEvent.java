package com.pte.practice.internal.event;

import java.util.UUID;

/** In-memory after-commit event; sensitive body is never persisted or logged. */
public record PracticeEmailChallengeRequestedEvent(
        String recipientEmail,
        UUID challengeId,
        String subject,
        String body) {
}
