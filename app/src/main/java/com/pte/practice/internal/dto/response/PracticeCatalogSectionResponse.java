package com.pte.practice.internal.dto.response;

import java.util.List;

public record PracticeCatalogSectionResponse(
        String code,
        String displayName,
        List<PracticeCatalogTaskResponse> taskTypes) {

    public PracticeCatalogSectionResponse {
        taskTypes = taskTypes == null ? List.of() : List.copyOf(taskTypes);
    }
}
