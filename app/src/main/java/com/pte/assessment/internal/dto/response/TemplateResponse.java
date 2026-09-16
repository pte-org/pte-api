package com.pte.assessment.internal.dto.response;

import java.util.List;
import java.util.UUID;

public record TemplateResponse(
        UUID publicId,
        String name,
        String description,
        UUID tenantId,
        String status,
        List<Section> sections) {

    public record Section(String section, int weightPercent, int orderIndex, List<Slot> slots) {
    }

    public record Slot(String taskType, int questionCount, int orderIndex) {
    }
}
