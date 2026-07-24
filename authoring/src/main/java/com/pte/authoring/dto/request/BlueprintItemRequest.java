package com.pte.authoring.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BlueprintItemRequest(
        @NotNull(message = "Question reference is required") UUID questionPublicId,
        @NotBlank(message = "Section is required") String section,
        int orderIndex) {
}
