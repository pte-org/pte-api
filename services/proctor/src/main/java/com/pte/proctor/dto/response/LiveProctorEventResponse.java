package com.pte.proctor.dto.response;

import com.pte.proctor.domain.enums.LiveProctorEventType;

import java.time.Instant;
import java.util.UUID;

public record LiveProctorEventResponse<T>(
        UUID eventId,
        LiveProctorEventType eventType,
        UUID sessionPublicId,
        Instant occurredAt,
        T data) {
}
