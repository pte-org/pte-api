package com.pte.session.internal.dto.response;

import com.pte.session.dto.response.ExamPolicyResponse;
import com.pte.session.domain.enums.FormMode;
import com.pte.session.domain.enums.ReusePolicy;
import com.pte.session.domain.enums.ExamMode;

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
        Integer capacity,
        UUID templatePublicId,
        Integer templateVersion,
        ExamMode examMode,
        FormMode formMode,
        ReusePolicy reusePolicy,
        String seriesKey,
        UUID generationJobPublicId,
        long draftVersion) {

    /** Source-compatible shape for callers that only need legacy sessions. */
    public SessionResponse(UUID publicId, String name, UUID tenantId, UUID subscriptionPublicId,
            UUID snapshotPublicId, Instant opensAt, Instant closesAt, String status,
            ExamPolicyResponse policy, Integer capacity) {
        this(publicId, name, tenantId, subscriptionPublicId, snapshotPublicId, opensAt, closesAt, status, policy,
                capacity, null, null, null, null, null, null, null, 0L);
    }
}
