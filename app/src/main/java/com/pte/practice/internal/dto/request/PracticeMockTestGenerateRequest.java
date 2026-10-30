package com.pte.practice.internal.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** How many new mock tests to build; they are numbered after the existing ones. */
public record PracticeMockTestGenerateRequest(
        @Min(value = 1, message = "count must be between 1 and 20")
        @Max(value = 20, message = "count must be between 1 and 20")
        int count) {
}
