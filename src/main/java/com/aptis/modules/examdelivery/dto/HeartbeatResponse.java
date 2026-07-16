package com.aptis.modules.examdelivery.dto;

import java.time.OffsetDateTime;

public record HeartbeatResponse(String status, OffsetDateTime lastHeartbeatAt) {
}
