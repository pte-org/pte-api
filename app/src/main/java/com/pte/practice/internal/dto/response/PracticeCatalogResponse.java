package com.pte.practice.internal.dto.response;

import java.util.List;

public record PracticeCatalogResponse(
        String productCode,
        String title,
        String catalogVersion,
        int timeLimitSeconds,
        List<PracticeCatalogSectionResponse> sections) {

    public PracticeCatalogResponse {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
