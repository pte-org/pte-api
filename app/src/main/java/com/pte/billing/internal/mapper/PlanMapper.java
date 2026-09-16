package com.pte.billing.internal.mapper;

import com.pte.billing.domain.Plan;
import com.pte.billing.internal.dto.response.PlanResponse;

public final class PlanMapper {

    private PlanMapper() {
    }

    public static PlanResponse toResponse(Plan plan) {
        return new PlanResponse(
                plan.getPublicId(),
                plan.getName(),
                plan.getDescription(),
                plan.getType().name(),
                plan.getPrice(),
                plan.getCurrency(),
                plan.getDurationDays(),
                plan.getMaxStudentsPerSession(),
                plan.getExtraStudentSlots(),
                plan.getStatus().name());
    }
}
