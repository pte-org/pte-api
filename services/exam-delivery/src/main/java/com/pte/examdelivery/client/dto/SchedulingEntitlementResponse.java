package com.pte.examdelivery.client.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * exam-delivery's own view of scheduling's entitlement response — not a shared
 * class. {@code replayPolicyType}/{@code answerIntegrityLevel} are plain
 * strings, not exam-delivery-local enums, matching the existing convention for
 * cross-service semantic values (see {@code taskType}/{@code section} in
 * {@link AuthoringSnapshotContentResponse.Item}) — exam-delivery never imports
 * another service's enum type.
 */
public record SchedulingEntitlementResponse(
        UUID sessionPublicId,
        UUID snapshotPublicId,
        UUID tenantId,
        Instant opensAt,
        Instant closesAt,
        Policy policy,
        List<CompositionItem> composition) {

    public record CompositionItem(String taskType, String section, int orderIndex, Integer timingOverrideSeconds,
            Integer maxPlayCount) {
    }

    public record Policy(String replayPolicyType, Integer replayPolicyLimit, Boolean deviceCheckRequired,
            Boolean proctorRequired, String answerIntegrityLevel) {
    }
}
