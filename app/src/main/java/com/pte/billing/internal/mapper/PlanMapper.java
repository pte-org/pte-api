package com.pte.billing.internal.mapper;

import com.pte.billing.domain.Plan;
import com.pte.billing.internal.dto.response.PlanResponse;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.internal.constant.BillingConstants;

public final class PlanMapper {

    private PlanMapper() {
    }

    public static PlanResponse toResponse(Plan plan) {
        return toResponse(plan, true, true);
    }

    public static PlanResponse toResponse(Plan plan, boolean referenced, boolean outstandingCodes) {
        boolean draft = !plan.isDeleted() && plan.getStatus() == PlanStatus.DRAFT;
        boolean active = !plan.isDeleted() && plan.getStatus() == PlanStatus.ACTIVE;
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
                plan.getStatus().name(),
                draft && !referenced,
                active && !outstandingCodes,
                !draft ? BillingConstants.PLAN_DELETE_DRAFT_ONLY : referenced ? BillingConstants.PLAN_HAS_REFERENCES : null,
                !active ? BillingConstants.PLAN_MUST_BE_ACTIVE_TO_ARCHIVE : outstandingCodes ? BillingConstants.PLAN_HAS_OUTSTANDING_CODES : null);
    }
}
