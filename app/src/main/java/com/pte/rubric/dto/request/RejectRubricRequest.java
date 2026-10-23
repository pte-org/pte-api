package com.pte.rubric.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRubricRequest(@NotBlank @Size(max = 1000) String reason) {
}
