package com.pte.notification.internal.dto.response;

import java.time.Instant;

public record InboxSnapshotResponse(String token, long upperSequence, Instant expiresAt) {
}
