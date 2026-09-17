package com.pte.session.internal.dto.request;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.session.domain.enums.ExamMode;
import com.pte.session.domain.enums.LockdownMode;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CreateSessionRequest(
        @NotBlank(message = SessionConstants.SESSION_NAME_REQUIRED) String name,
        @NotNull(message = SessionConstants.SUBSCRIPTION_REFERENCE_REQUIRED) UUID subscriptionPublicId,
        /** 1–4 distinct {@code PteSection} names — the exam is randomly generated from these skills. */
        @NotEmpty(message = SessionConstants.SKILLS_REQUIRED)
        @Size(min = 1, max = 4, message = SessionConstants.SKILLS_SIZE_INVALID) Set<String> skills,
        @NotNull(message = SessionConstants.OPEN_TIME_REQUIRED) @Future(message = SessionConstants.OPEN_TIME_FUTURE) Instant opensAt,
        @NotNull(message = SessionConstants.CLOSE_TIME_REQUIRED) Instant closesAt,
        /** Null defaults to {@link ExamMode#MOCK_TEST} — the safe middle ground, not the permissive PRACTICE default. */
        ExamMode examMode,
        /** Optional teacher override — null means use ExamMode default.
         *  Validation: STRICT is not allowed when examMode is PRACTICE. */
        LockdownMode lockdownMode,
        @NotNull(message = SessionConstants.CAPACITY_REQUIRED)
        @Positive(message = SessionConstants.CAPACITY_POSITIVE) Integer capacity) {
}
