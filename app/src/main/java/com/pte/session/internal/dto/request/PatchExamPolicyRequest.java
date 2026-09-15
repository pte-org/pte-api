package com.pte.session.internal.dto.request;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.session.domain.enums.AnswerIntegrityLevel;
import com.pte.session.domain.enums.LockdownMode;
import com.pte.session.domain.enums.ReplayPolicyType;
import jakarta.validation.constraints.Positive;

/**
 * Partial update — every field is optional; omitted (null) means "leave unchanged."
 * {@code replayPolicyLimit} is only meaningful when {@code replayPolicyType=LIMITED};
 * both must be provided together to change the replay policy.
 */
public record PatchExamPolicyRequest(
        ReplayPolicyType replayPolicyType,
        @Positive(message = SessionConstants.REPLAY_POLICY_LIMIT_POSITIVE) Integer replayPolicyLimit,
        Boolean deviceCheckRequired,
        Boolean proctorRequired,
        AnswerIntegrityLevel answerIntegrityLevel,
        LockdownMode lockdownMode) {
}
