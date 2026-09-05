package com.pte.scheduling.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What exam-delivery needs to pin an attempt, returned only after verifying
 * the calling student is enrolled and the session is open. Internal
 * service-to-service surface only.
 */
public record EntitlementResponse(
        UUID sessionPublicId,
        UUID snapshotPublicId,
        UUID tenantId,
        Instant opensAt,
        Instant closesAt,
        ExamPolicyResponse policy,
        List<CompositionItemResponse> composition) {
}
