package com.pte.scoring.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SubmitExaminerScoreRequest(
        @NotNull @Min(0) @Max(100) Integer score) {
}
