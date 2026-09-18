package com.pte.assessment.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BlueprintItemRequest(
        @NotNull UUID questionPublicId,
        String section,
        Integer orderIndex) {
}
