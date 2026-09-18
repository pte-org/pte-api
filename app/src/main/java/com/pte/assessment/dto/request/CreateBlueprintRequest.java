package com.pte.assessment.dto.request;

import com.pte.assessment.internal.constant.AssessmentConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateBlueprintRequest(
        @NotBlank(message = AssessmentConstants.BLUEPRINT_NAME_REQUIRED) String name,
        @NotEmpty(message = AssessmentConstants.BLUEPRINT_ITEMS_REQUIRED) @Valid List<BlueprintItemRequest> items,
        Long version,
        Boolean preserveOrder) {

    public CreateBlueprintRequest(String name, List<BlueprintItemRequest> items, Long version) {
        this(name, items, version, false);
    }
}
