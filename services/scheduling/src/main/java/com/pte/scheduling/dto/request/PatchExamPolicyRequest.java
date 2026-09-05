package com.pte.scheduling.dto.request;

import com.pte.scheduling.domain.enums.AnswerIntegrityLevel;
import com.pte.scheduling.domain.enums.ReplayPolicyType;
import jakarta.validation.constraints.Positive;

/**
 * Partial update — every field is optional; omitted (null) means "leave unchanged."
 * None of these fields has a legitimate business meaning of "explicitly null,"
 * so plain nullable fields (no Optional-wrapper ceremony) unambiguously encode
 * "not provided." {@code replayPolicyLimit} is only meaningful when
 * {@code replayPolicyType=LIMITED}; both must be provided together to change
 * the replay policy.
 */
public record PatchExamPolicyRequest(
        ReplayPolicyType replayPolicyType,
        @Positive(message = "Replay policy limit must be positive if provided") Integer replayPolicyLimit,
        Boolean deviceCheckRequired,
        Boolean proctorRequired,
        AnswerIntegrityLevel answerIntegrityLevel) {
}
