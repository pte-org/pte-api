package com.pte.assessment.dto.response;

import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.domain.enums.PteTaskType;

import java.util.List;
import java.util.UUID;

/** Stable assessment-to-resolver contract for an active template's structure. */
public record TemplateSpec(UUID templatePublicId, String name, List<Section> sections) {

    public record Section(PteSection section, int weightPercent, int orderIndex, List<Slot> slots) {
    }

    public record Slot(PteTaskType taskType, int questionCount, int orderIndex) {
    }
}
