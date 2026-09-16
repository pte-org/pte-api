package com.pte.assessment.internal.mapper;

import com.pte.assessment.domain.ExamTemplate;
import com.pte.assessment.domain.TemplateSection;
import com.pte.assessment.domain.TemplateSlot;
import com.pte.assessment.dto.response.TemplateSpec;
import com.pte.assessment.internal.dto.response.TemplateResponse;

public final class TemplateMapper {

    private TemplateMapper() {
    }

    public static TemplateResponse toResponse(ExamTemplate template) {
        return new TemplateResponse(
                template.getPublicId(),
                template.getName(),
                template.getDescription(),
                template.getTenantId(),
                template.getStatus().name(),
                template.getSections().stream().map(TemplateMapper::toResponse).toList());
    }

    public static TemplateSpec toSpec(ExamTemplate template) {
        return new TemplateSpec(
                template.getPublicId(),
                template.getName(),
                template.getSections().stream().map(TemplateMapper::toSpec).toList());
    }

    private static TemplateResponse.Section toResponse(TemplateSection section) {
        return new TemplateResponse.Section(
                section.getSection().name(),
                section.getWeightPercent(),
                section.getOrderIndex(),
                section.getSlots().stream().map(TemplateMapper::toResponse).toList());
    }

    private static TemplateResponse.Slot toResponse(TemplateSlot slot) {
        return new TemplateResponse.Slot(slot.getTaskType().name(), slot.getQuestionCount(), slot.getOrderIndex());
    }

    private static TemplateSpec.Section toSpec(TemplateSection section) {
        return new TemplateSpec.Section(
                section.getSection(),
                section.getWeightPercent(),
                section.getOrderIndex(),
                section.getSlots().stream().map(TemplateMapper::toSpec).toList());
    }

    private static TemplateSpec.Slot toSpec(TemplateSlot slot) {
        return new TemplateSpec.Slot(slot.getTaskType(), slot.getQuestionCount(), slot.getOrderIndex());
    }
}
