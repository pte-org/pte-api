package com.pte.practice.internal.dto.request;

import com.pte.attempt.ResponseConfidence;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Optional current-item draft sent when a student leaves a session. */
public record PracticeSaveAndExitRequest(
        @NotNull @PositiveOrZero Long clientVersion,
        UUID itemPublicId,
        @Size(max = 16_384) String payload,
        ResponseConfidence confidence) {
}
