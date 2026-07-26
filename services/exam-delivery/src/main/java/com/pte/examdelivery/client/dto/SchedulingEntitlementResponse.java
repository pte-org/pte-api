package com.pte.examdelivery.client.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** exam-delivery's own view of scheduling's entitlement response — not a shared class. */
public record SchedulingEntitlementResponse(
        UUID sessionPublicId,
        UUID snapshotPublicId,
        UUID tenantId,
        Instant opensAt,
        Instant closesAt,
        List<CompositionItem> composition) {

    public record CompositionItem(String taskType, String section, int orderIndex, Integer timingOverrideSeconds) {
    }
}
