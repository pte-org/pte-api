package com.pte.authoring.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateBlueprintRequest(
        @NotBlank(message = "Blueprint name is required") String name,
        @NotEmpty(message = "A blueprint needs at least one item")
        @Valid List<BlueprintItemRequest> items) {
}
