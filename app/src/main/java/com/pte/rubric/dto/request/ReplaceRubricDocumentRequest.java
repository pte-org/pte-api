package com.pte.rubric.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReplaceRubricDocumentRequest(
        @NotBlank @Size(max = 1_000_000) String documentJson,
        @Size(max = 1000) String changeReason,
        Long expectedVersion) {
}
