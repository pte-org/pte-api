package com.pte.billing.internal.service;

import com.pte.billing.domain.ServicePlan;

import java.math.BigDecimal;

/** Business snapshot of a catalogue plan (price, benefits, lifecycle) for the audit trail. */
record PlanAuditState(
        String name,
        String description,
        String type,
        BigDecimal price,
        String currency,
        Integer durationDays,
        Integer maxStudentsPerSession,
        Integer extraStudentSlots,
        String status) {

    static PlanAuditState of(ServicePlan plan) {
        return new PlanAuditState(plan.getName(), plan.getDescription(),
                plan.getType() == null ? null : plan.getType().name(), plan.getPrice(), plan.getCurrency(),
                plan.getDurationDays(), plan.getMaxStudentsPerSession(), plan.getExtraStudentSlots(),
                plan.getStatus() == null ? null : plan.getStatus().name());
    }
}
