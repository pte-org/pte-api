package com.pte.practice.internal.dto.response;

import java.util.List;

/** Safe runtime metadata; it deliberately contains no answer or reference data. */
public record PracticeCatalogTaskResponse(
        String code,
        String displayName,
        String section,
        boolean scored,
        PracticeCatalogAvailability availability,
        String unavailableReason,
        String profileKey,
        Integer profileVersion,
        String rendererKey,
        Integer contractVersion,
        Integer answerSchemaVersion,
        List<String> requiredClientCapabilities,
        String contentStatus,
        String provenance) {

    public PracticeCatalogTaskResponse {
        requiredClientCapabilities = requiredClientCapabilities == null
                ? List.of() : List.copyOf(requiredClientCapabilities);
    }
}
