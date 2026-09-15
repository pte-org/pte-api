package com.pte.session.internal.dto.request;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.session.domain.enums.ExamMode;
import com.pte.session.domain.enums.LockdownMode;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.UUID;

public record CreateSessionRequest(
        @NotBlank(message = SessionConstants.SESSION_NAME_REQUIRED) String name,
        @NotNull(message = SessionConstants.SNAPSHOT_REFERENCE_REQUIRED) UUID snapshotPublicId,
        @NotNull(message = SessionConstants.OPEN_TIME_REQUIRED) @Future(message = SessionConstants.OPEN_TIME_FUTURE) Instant opensAt,
        @NotNull(message = SessionConstants.CLOSE_TIME_REQUIRED) Instant closesAt,
        /** Null defaults to {@link ExamMode#MOCK_TEST} — the safe middle ground, not the permissive PRACTICE default. */
        ExamMode examMode,
        /** Optional teacher override — null means use ExamMode default.
         *  Validation: STRICT is not allowed when examMode is PRACTICE. */
        LockdownMode lockdownMode,
        /** Null = unlimited, matching {@link com.pte.session.domain.ExamSession#getCapacity()}. */
        @Positive(message = SessionConstants.CAPACITY_POSITIVE) Integer capacity) {
}
