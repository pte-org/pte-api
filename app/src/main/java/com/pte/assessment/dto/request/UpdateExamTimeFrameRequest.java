package com.pte.assessment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Sets the Pearson time window of a DRAFT template; every value is in seconds. */
public record UpdateExamTimeFrameRequest(
        @NotNull @Positive Integer speakingWritingMinSeconds,
        @NotNull @Positive Integer speakingWritingMaxSeconds,
        @NotNull @Positive Integer readingMinSeconds,
        @NotNull @Positive Integer readingMaxSeconds,
        @NotNull @Positive Integer listeningMinSeconds,
        @NotNull @Positive Integer listeningMaxSeconds,
        @NotNull @Positive Integer totalMaxSeconds) {
}
