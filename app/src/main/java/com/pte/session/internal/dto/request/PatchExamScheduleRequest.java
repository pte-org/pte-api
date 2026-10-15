package com.pte.session.internal.dto.request;

import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Updates the identity and schedule of a scheduled exam, or extends the close
 * time of an open exam. Null fields retain their current value.
 */
public record PatchExamScheduleRequest(
        @Size(max = 255) String name,
        Instant opensAt,
        Instant closesAt,
        Long expectedVersion) {
}
