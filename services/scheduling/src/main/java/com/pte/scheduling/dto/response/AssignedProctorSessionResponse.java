package com.pte.scheduling.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AssignedProctorSessionResponse(
        UUID assignmentPublicId,
        UUID sessionPublicId,
        String name,
        Instant opensAt,
        Instant closesAt,
        String status) {
}
