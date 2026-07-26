package com.pte.authoring.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OptionRequest(
        @NotBlank(message = "Option text is required") String text,
        boolean correct,
        int orderIndex) {
}
