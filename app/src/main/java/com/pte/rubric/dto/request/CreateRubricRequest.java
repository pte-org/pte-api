package com.pte.rubric.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRubricRequest(
        @NotBlank @Size(max = 128) String rubricKey,
        @NotBlank @Size(max = 64) String taskTypeKey,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 1_000_000) String documentJson,
        @Size(max = 1000) String changeReason) {
}
