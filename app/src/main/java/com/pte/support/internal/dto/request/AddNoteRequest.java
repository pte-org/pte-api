package com.pte.support.internal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddNoteRequest(
        @NotBlank @Size(min = 1, max = 2000) String content
) {
}
