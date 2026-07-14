package com.aptis.modules.iam.dto.request.studentimport;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PreviewRequest(
        @NotBlank String importId,
        @NotNull Map<String, String> columnMappings,
        @Valid @NotNull UsernamePatternConfig usernamePatternConfig) {
}
