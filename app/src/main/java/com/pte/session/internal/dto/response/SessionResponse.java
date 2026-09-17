package com.pte.session.internal.dto.response;

import com.pte.session.dto.response.ExamPolicyResponse;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID publicId,
        String name,
        UUID tenantId,
        UUID subscriptionPublicId,
        UUID snapshotPublicId,
        Instant opensAt,
        Instant closesAt,
        String status,
        ExamPolicyResponse policy,
        Integer capacity) {
}
