package com.pte.session.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What {@code attempt} needs to pin an attempt, returned only after verifying
 * the calling student is enrolled and the session is open. Trusted
 * application-call surface only — see {@link com.pte.session.SessionService#checkEntitlement}.
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
