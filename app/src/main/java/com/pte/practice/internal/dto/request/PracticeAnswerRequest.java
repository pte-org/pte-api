package com.pte.practice.internal.dto.request;

import com.pte.attempt.ResponseConfidence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Student answer payload; it contains no client-supplied correctness flag. */
public record PracticeAnswerRequest(
        @NotNull @PositiveOrZero Long clientVersion,
        @NotBlank String payload,
        @NotNull ResponseConfidence confidence) {
}
