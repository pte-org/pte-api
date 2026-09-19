package com.pte.scoretemplate.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Imports an existing template export as a new editable DRAFT. */
public record ImportScoreTemplateRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 255) String name,
        @NotEmpty @Valid List<ScoreTemplateItemRequest> items) {
}
