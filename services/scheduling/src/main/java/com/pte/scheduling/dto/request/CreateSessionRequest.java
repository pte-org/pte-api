package com.pte.scheduling.dto.request;

import com.pte.scheduling.domain.enums.ExamMode;
import com.pte.scheduling.domain.enums.LockdownMode;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.UUID;

public record CreateSessionRequest(
        @NotBlank(message = "Session name is required") String name,
        @NotNull(message = "Snapshot reference is required") UUID snapshotPublicId,
        @NotNull(message = "Open time is required") @Future(message = "Open time must be in the future") Instant opensAt,
        @NotNull(message = "Close time is required") Instant closesAt,
        /** Null defaults to {@link ExamMode#MOCK_TEST} — the safe middle ground, not the permissive PRACTICE default. */
        ExamMode examMode,
        /** Optional teacher override — null means use ExamMode default.
         *  Validation: STRICT is not allowed when examMode is PRACTICE. */
        LockdownMode lockdownMode,
        /** Null = unlimited, matching {@link com.pte.scheduling.domain.ExamSession#getCapacity()}. */
        @Positive(message = "Capacity must be positive") Integer capacity) {
}
