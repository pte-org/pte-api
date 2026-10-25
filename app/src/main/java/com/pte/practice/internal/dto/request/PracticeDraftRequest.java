package com.pte.practice.internal.dto.request;

import com.pte.attempt.ResponseConfidence;
import com.pte.practice.internal.constant.PracticeConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Saves the current item's work without leaving the session: a finished recording right after it uploads, or a
 * re-recording that replaces the saved one. Unlike save-and-exit the session stays {@code IN_PROGRESS}.
 */
public record PracticeDraftRequest(
        @NotNull @PositiveOrZero Long clientVersion,
        @NotBlank @Size(max = PracticeConstants.MAX_ANSWER_PAYLOAD_LENGTH) String payload,
        ResponseConfidence confidence) {
}
