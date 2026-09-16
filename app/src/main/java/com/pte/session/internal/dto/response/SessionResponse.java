package com.pte.session.internal.dto.response;

import com.pte.session.dto.response.CompositionItemResponse;
import com.pte.session.dto.response.ExamPolicyResponse;

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
        ExamPolicyResponse policy,
        List<CompositionItemResponse> composition,
        /** Null = unlimited. */
        Integer capacity) {
}
