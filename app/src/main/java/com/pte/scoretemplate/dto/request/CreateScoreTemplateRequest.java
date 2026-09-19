package com.pte.scoretemplate.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Creates an empty editable DRAFT template. Items are added in the admin editor. */
public record CreateScoreTemplateRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 255) String name) {
}
