package com.pte.session.internal.mapper;

import com.pte.session.domain.ExamPolicy;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.ExamMode;
import com.pte.session.domain.enums.LockdownMode;
import com.pte.session.dto.response.ExamPolicyResponse;
import com.pte.session.internal.constant.SessionConstants;
import com.pte.session.internal.dto.response.SessionResponse;
import com.pte.session.internal.policy.SessionPolicyResolver;

public final class SessionMapper {

    private SessionMapper() {
    }

    public static SessionResponse toResponse(ExamSession session) {
        boolean legacySession = session.getExamMode() == null;
        return new SessionResponse(
                session.getPublicId(),
                session.getSessionCode(),
                session.getName(),
                session.getTenantId(),
                session.getSubscriptionId(),
                session.getSnapshotPublicId(),
                session.getOpensAt(),
                session.getClosesAt(),
                session.getStatus().name(),
                toPolicy(session.getPolicy(), session.getExamMode(), legacySession),
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
     * ExamPolicy.forMode() sets every field, while backfillLegacyDefaults()
     * restores the four pre-lockdown fields and deliberately leaves lockdown
     * mode for session-boundary resolution. A partially-null policy still means
     * corrupted state, so fail loudly instead of silently weakening it.
     */
    public static ExamPolicyResponse toPolicy(ExamPolicy policy) {
        validateCompletePolicy(policy);
        return new ExamPolicyResponse(
                policy.getReplayPolicy().type().name(),
                policy.getReplayPolicy().limit(),
                policy.getDeviceCheckRequired(),
                policy.getProctorRequired(),
                policy.getAnswerIntegrityLevel().name(),
                policy.getLockdownMode() != null ? policy.getLockdownMode().name() : null);
    }

    /** Maps a session policy after applying the explicit legacy compatibility decision. */
    public static ExamPolicyResponse toPolicy(ExamPolicy policy, ExamMode examMode, boolean legacyState) {
        validateCompletePolicy(policy);
        LockdownMode effectiveLockdownMode = SessionPolicyResolver.resolveEffectiveLockdownMode(
                examMode, policy.getLockdownMode(), legacyState);
        return new ExamPolicyResponse(
                policy.getReplayPolicy().type().name(),
                policy.getReplayPolicy().limit(),
                policy.getDeviceCheckRequired(),
                policy.getProctorRequired(),
                policy.getAnswerIntegrityLevel().name(),
                effectiveLockdownMode.name());
    }

    private static void validateCompletePolicy(ExamPolicy policy) {
        if (policy == null || policy.getReplayPolicyType() == null || policy.getAnswerIntegrityLevel() == null) {
            throw new IllegalStateException(SessionConstants.EXAM_POLICY_INCOMPLETE);
        }
    }
}
