package com.pte.authoring.mapper;

import com.pte.authoring.domain.BlueprintItem;
import com.pte.authoring.domain.ExamBlueprint;
import com.pte.authoring.dto.response.BlueprintResponse;

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
                items);
    }

    private static BlueprintResponse.Item toItem(BlueprintItem item) {
        return new BlueprintResponse.Item(item.getQuestionPublicId(), item.getSection().name(), item.getOrderIndex());
    }
}
