package com.pte.scheduling.mapper;

import com.pte.scheduling.domain.ExamPolicy;
import com.pte.scheduling.domain.ExamSession;
import com.pte.scheduling.domain.SessionComposition;
import com.pte.scheduling.dto.response.CompositionItemResponse;
import com.pte.scheduling.dto.response.ExamPolicyResponse;
import com.pte.scheduling.dto.response.SessionResponse;

import java.util.List;

public final class SessionMapper {

    private SessionMapper() {
    }

    public static SessionResponse toResponse(ExamSession session) {
        List<CompositionItemResponse> composition = session.getComposition().stream()
                .map(SessionMapper::toItem)
                .toList();
        return new SessionResponse(
                session.getPublicId(),
                session.getName(),
                session.getTenantId(),
                session.getSnapshotPublicId(),
                session.getOpensAt(),
                session.getClosesAt(),
                session.getStatus().name(),
                toPolicy(session.getPolicy()),
                composition);
    }

    /**
     * {@code ExamPolicy.forMode()}/{@code backfillLegacyDefaults()} always set all
     * 5 fields together, and {@code patchPolicy()} only ever leaves fields
     * unchanged (never nulls one out) — so a partially-null policy here means
     * corrupted state, not a normal null-safety case. Fail loudly rather than
     * silently substitute a default: a silent MOCK_TEST/PRACTICE fallback could
     * quietly weaken a REAL_EXAM session's replay/proctor/integrity guarantees,
     * which is worse than a 500.
     */
    public static ExamPolicyResponse toPolicy(ExamPolicy policy) {
        if (policy == null || policy.getReplayPolicyType() == null || policy.getAnswerIntegrityLevel() == null) {
            throw new IllegalStateException("ExamPolicy is incomplete — expected all fields set together");
        }
        return new ExamPolicyResponse(
                policy.getReplayPolicy().type().name(),
                policy.getReplayPolicy().limit(),
                policy.getDeviceCheckRequired(),
                policy.getProctorRequired(),
                policy.getAnswerIntegrityLevel().name());
    }

    public static CompositionItemResponse toItem(SessionComposition item) {
        return new CompositionItemResponse(item.getTaskType(), item.getSection(), item.getOrderIndex(),
                item.getTimingOverrideSeconds(), item.getMaxPlayCount());
    }
}
