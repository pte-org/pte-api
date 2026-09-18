package com.pte.assessment.internal.mapper;

import com.pte.assessment.domain.BlueprintItem;
import com.pte.assessment.domain.ExamBlueprint;
import com.pte.assessment.internal.dto.response.BlueprintResponse;

import java.util.List;

public final class BlueprintMapper {

    private BlueprintMapper() {
    }

    public static BlueprintResponse toResponse(ExamBlueprint blueprint) {
        List<BlueprintResponse.Item> items = blueprint.getItems().stream()
                .map(BlueprintMapper::toItem)
                .toList();
        return new BlueprintResponse(
                blueprint.getPublicId(),
                blueprint.getName(),
                blueprint.getTenantId(),
                blueprint.getStatus().name(),
                blueprint.getRejectionReason(),
                blueprint.getVersion(),
                items);
    }

    private static BlueprintResponse.Item toItem(BlueprintItem item) {
        return new BlueprintResponse.Item(item.getQuestionPublicId(), item.getSection().name(), item.getOrderIndex());
    }
}
