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
        int maxRetriesPerStudent,
        /** CEFR mix chosen for this exam; empty when it follows its template's. */
        java.util.List<com.pte.assessment.dto.response.CefrTargetResponse> cefrTargets,
        /** The exam prefers questions the centre has not used in its earlier exams. */
        boolean avoidRepeatedQuestions) {

    /** Constructor for callers that do not carry the repeat preference. */
    public SessionResponse(UUID publicId, String sessionCode, String name, UUID tenantId,
            UUID subscriptionPublicId, UUID examVersionPublicId, Instant opensAt, Instant closesAt, String status,
            ExamPolicyResponse policy, Integer capacity, UUID examDefinitionPublicId,
            Integer examDefinitionVersion, ExamMode examMode, ReusePolicy reusePolicy, String seriesKey,
            long draftVersion, Set<String> selectedSkills, int maxRetriesPerStudent,
            java.util.List<com.pte.assessment.dto.response.CefrTargetResponse> cefrTargets) {
        this(publicId, sessionCode, name, tenantId, subscriptionPublicId, examVersionPublicId, opensAt, closesAt,
                status, policy, capacity, examDefinitionPublicId, examDefinitionVersion, examMode, reusePolicy,
                seriesKey, draftVersion, selectedSkills, maxRetriesPerStudent, cefrTargets, false);
    }

    /** Constructor for callers that do not carry the CEFR mix. */
    public SessionResponse(UUID publicId, String sessionCode, String name, UUID tenantId,
            UUID subscriptionPublicId, UUID examVersionPublicId, Instant opensAt, Instant closesAt, String status,
            ExamPolicyResponse policy, Integer capacity, UUID examDefinitionPublicId,
            Integer examDefinitionVersion, ExamMode examMode, ReusePolicy reusePolicy, String seriesKey,
            long draftVersion, Set<String> selectedSkills, int maxRetriesPerStudent) {
        this(publicId, sessionCode, name, tenantId, subscriptionPublicId, examVersionPublicId, opensAt, closesAt,
                status, policy, capacity, examDefinitionPublicId, examDefinitionVersion, examMode, reusePolicy,
                seriesKey, draftVersion, selectedSkills, maxRetriesPerStudent, java.util.List.of());
    }

    /** Source-compatible shape for callers that only need legacy sessions. */
    public SessionResponse(UUID publicId, String name, UUID tenantId, UUID subscriptionPublicId,
            UUID examVersionPublicId, Instant opensAt, Instant closesAt, String status,
            ExamPolicyResponse policy, Integer capacity) {
        this(publicId, null, name, tenantId, subscriptionPublicId, examVersionPublicId, opensAt, closesAt, status, policy,
                capacity, null, null, null, null, null, 0L, Set.of(), 0);
    }
}
