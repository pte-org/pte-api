package com.pte.scheduling.domain;

import com.pte.scheduling.domain.enums.AnswerIntegrityLevel;
import com.pte.scheduling.domain.enums.ExamMode;
import com.pte.scheduling.domain.enums.ReplayPolicyType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PostLoad;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Session-scoped exam policy, resolved once from {@link ExamMode} at session
 * creation (see {@link #forMode(ExamMode)}) and immutable thereafter except
 * via the pre-open {@code PATCH /sessions/{id}/policy} override. Pinned
 * read-only into exam-delivery's {@code PinnedExamSnapshot} at StartAttempt —
 * never re-fetched for an attempt's lifetime.
 *
 * <p>Columns are deliberately nullable (no {@code nullable = false}) even
 * though every newly-created policy always sets them: this service has no
 * migration tool (Hibernate {@code ddl-auto: update} only), so sessions
 * created before this feature existed have NULL here until first touched.
 * {@link #backfillLegacyDefaults()} normalizes those in memory on load —
 * see {@code ExamSession}'s {@code @PostLoad}.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class ExamPolicy {

    @Enumerated(EnumType.STRING)
    @Column(name = "replay_policy_type")
    private ReplayPolicyType replayPolicyType;

    @Column(name = "replay_policy_limit")
    private Integer replayPolicyLimit;

    @Column(name = "device_check_required")
    private Boolean deviceCheckRequired;

    @Column(name = "proctor_required")
    private Boolean proctorRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_integrity_level")
    private AnswerIntegrityLevel answerIntegrityLevel;

    public ReplayPolicy getReplayPolicy() {
        return ReplayPolicy.of(replayPolicyType, replayPolicyLimit);
    }

    public void setReplayPolicy(ReplayPolicy replayPolicy) {
        this.replayPolicyType = replayPolicy.type();
        this.replayPolicyLimit = replayPolicy.limit();
    }

    /** Legacy row (created before this feature existed) never had a policy — treat as MOCK_TEST. */
    @PostLoad
    void backfillLegacyDefaults() {
        if (replayPolicyType == null) {
            ExamPolicy fallback = mockTestDefault();
            this.replayPolicyType = fallback.replayPolicyType;
            this.replayPolicyLimit = fallback.replayPolicyLimit;
            this.deviceCheckRequired = fallback.deviceCheckRequired;
            this.proctorRequired = fallback.proctorRequired;
            this.answerIntegrityLevel = fallback.answerIntegrityLevel;
        }
    }

    public static ExamPolicy practiceDefault() {
        return build(ReplayPolicy.unlimited(), false, false, AnswerIntegrityLevel.STANDARD);
    }

    public static ExamPolicy mockTestDefault() {
        return build(ReplayPolicy.limited(3), true, false, AnswerIntegrityLevel.STANDARD);
    }

    public static ExamPolicy realExamDefault() {
        return build(ReplayPolicy.limited(1), true, true, AnswerIntegrityLevel.STRICT);
    }

    /** Pure mapping, resolved exactly once at {@code SessionService.create()} time. */
    public static ExamPolicy forMode(ExamMode mode) {
        return switch (mode) {
            case PRACTICE -> practiceDefault();
            case MOCK_TEST -> mockTestDefault();
            case REAL_EXAM -> realExamDefault();
        };
    }

    private static ExamPolicy build(ReplayPolicy replayPolicy, boolean deviceCheckRequired,
                                     boolean proctorRequired, AnswerIntegrityLevel answerIntegrityLevel) {
        ExamPolicy policy = new ExamPolicy();
        policy.setReplayPolicy(replayPolicy);
        policy.setDeviceCheckRequired(deviceCheckRequired);
        policy.setProctorRequired(proctorRequired);
        policy.setAnswerIntegrityLevel(answerIntegrityLevel);
        return policy;
    }
}
