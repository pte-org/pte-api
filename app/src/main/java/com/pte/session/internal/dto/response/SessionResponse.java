package com.pte.session.internal.dto.response;

import com.pte.session.dto.response.ExamPolicyResponse;
import com.pte.session.domain.enums.ReusePolicy;
import com.pte.session.domain.enums.ExamMode;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record SessionResponse(
        UUID publicId,
        String sessionCode,
        String name,
        UUID tenantId,
        UUID subscriptionPublicId,
        UUID examVersionPublicId,
        Instant opensAt,
        Instant closesAt,
        String status,
        ExamPolicyResponse policy,
        Integer capacity,
        UUID examDefinitionPublicId,
        Integer examDefinitionVersion,
        ExamMode examMode,
        ReusePolicy reusePolicy,
        String seriesKey,
        long draftVersion,
        Set<String> selectedSkills,
        int maxRetriesPerStudent) {

    /** Source-compatible shape for callers that only need legacy sessions. */
    public SessionResponse(UUID publicId, String name, UUID tenantId, UUID subscriptionPublicId,
            UUID examVersionPublicId, Instant opensAt, Instant closesAt, String status,
            ExamPolicyResponse policy, Integer capacity) {
        this(publicId, null, name, tenantId, subscriptionPublicId, examVersionPublicId, opensAt, closesAt, status, policy,
                capacity, null, null, null, null, null, 0L, Set.of(), 0);
    }
}
