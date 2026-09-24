package com.pte.session.internal.mapper;

import com.pte.session.domain.ExamPolicy;
import com.pte.session.domain.ExamSession;
import com.pte.session.dto.response.ExamPolicyResponse;
import com.pte.session.internal.constant.SessionConstants;
import com.pte.session.internal.dto.response.SessionResponse;

public final class SessionMapper {

    private SessionMapper() {
    }

    public static SessionResponse toResponse(ExamSession session) {
        return new SessionResponse(
                session.getPublicId(),
                session.getName(),
                session.getTenantId(),
                session.getSubscriptionId(),
                session.getSnapshotPublicId(),
                session.getOpensAt(),
                session.getClosesAt(),
                session.getStatus().name(),
                toPolicy(session.getPolicy()),
                session.getCapacity(),
                session.getTemplatePublicId(),
                session.getTemplateVersion(),
                session.getExamMode(),
                session.getFormMode(),
                session.getReusePolicy(),
                session.getSeriesKey(),
                session.getGenerationJobPublicId(),
                session.getDraftVersion() == null ? 0L : session.getDraftVersion(),
                session.getSelectedSkills() == null || session.getSelectedSkills().isEmpty()
                        ? null : java.util.Set.copyOf(session.getSelectedSkills()),
                session.getMaxRetriesPerStudent());
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
            throw new IllegalStateException(SessionConstants.EXAM_POLICY_INCOMPLETE);
        }
        return new ExamPolicyResponse(
                policy.getReplayPolicy().type().name(),
                policy.getReplayPolicy().limit(),
                policy.getDeviceCheckRequired(),
                policy.getProctorRequired(),
                policy.getAnswerIntegrityLevel().name(),
                policy.getLockdownMode() != null ? policy.getLockdownMode().name() : null);
    }
}
