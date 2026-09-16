package com.pte.assessment.internal.dto.response;

import java.util.List;
import java.util.UUID;

public record BlueprintResponse(
        UUID publicId,
        String name,
        UUID tenantId,
        String status,
        List<Item> items) {

    public record Item(UUID questionPublicId, String section, int orderIndex) {
    }
}
