package com.pte.scheduling.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionResponse(
        UUID publicId,
        String name,
        UUID tenantId,
        UUID snapshotPublicId,
        Instant opensAt,
        Instant closesAt,
        String status,
        List<CompositionItemResponse> composition) {
}
