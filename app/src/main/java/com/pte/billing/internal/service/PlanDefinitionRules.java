package com.pte.billing.internal.service;

import com.pte.billing.domain.ServicePlan;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.exception.plan.PlanValidationException;

import java.math.BigDecimal;
import java.util.Locale;

/** Parses, validates and applies the editable definition of a plan (name, price, type and benefits). */
final class PlanDefinitionRules {

    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_DESCRIPTION_LENGTH = 255;
    private static final int MAX_EXAM_DURATION_DAYS = 3650;

    private PlanDefinitionRules() {
    }

    static void apply(ServicePlan plan, String name, String description, BigDecimal price, String currency,
            PlanType type, Integer durationDays, Integer maxStudentsPerSession, Integer extraStudentSlots) {
        plan.setName(name.trim());
        plan.setDescription(description == null || description.trim().isEmpty() ? null : description.trim());
        plan.setType(type);
        plan.setPrice(price);
        plan.setCurrency(currency.trim().toUpperCase(Locale.ROOT));
        plan.setDurationDays(durationDays);
        plan.setMaxStudentsPerSession(maxStudentsPerSession);
        plan.setExtraStudentSlots(extraStudentSlots);
    }

    static PlanType parseType(String value) {
        if (value == null || value.isBlank()) {
            throw new PlanValidationException(BillingConstants.PLAN_TYPE_REQUIRED);
        }
        try {
            return PlanType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new PlanValidationException(BillingConstants.PLAN_TYPE_INVALID);
        }
    }

    static void validate(String name, String description, BigDecimal price, String currency, PlanType type,
            Integer durationDays, Integer maxStudentsPerSession, Integer extraStudentSlots) {
        String normalizedName = name == null ? null : name.trim();
        if (normalizedName == null || normalizedName.isEmpty()) {
            throw new PlanValidationException(BillingConstants.PLAN_NAME_REQUIRED);
        }
        if (normalizedName.length() > MAX_NAME_LENGTH) {
            throw new PlanValidationException(BillingConstants.PLAN_NAME_MAX);
        }
        if (description != null && description.trim().length() > MAX_DESCRIPTION_LENGTH) {
            throw new PlanValidationException(BillingConstants.PLAN_DESCRIPTION_MAX);
        }
        if (price == null) {
            throw new PlanValidationException(BillingConstants.PLAN_PRICE_REQUIRED);
        }
        if (price.signum() < 0) {
            throw new PlanValidationException(BillingConstants.PLAN_PRICE_NON_NEGATIVE);
        }
        if (price.scale() > 2 || price.precision() - price.scale() > 17) {
            throw new PlanValidationException(BillingConstants.PLAN_PRICE_PRECISION_INVALID);
        }
        if (currency == null || currency.isBlank()) {
            throw new PlanValidationException(BillingConstants.PLAN_CURRENCY_REQUIRED);
        }
        if (!"VND".equals(currency.trim().toUpperCase(Locale.ROOT))) {
            throw new PlanValidationException(BillingConstants.PLAN_CURRENCY_INVALID);
        }
        if (type == null) {
            throw new PlanValidationException(BillingConstants.PLAN_TYPE_INVALID);
        }

        if (type == PlanType.EXAM_PACKAGE) {
            validateExamBenefits(durationDays, maxStudentsPerSession, extraStudentSlots);
            return;
        }

        if (!positive(extraStudentSlots)) {
            throw new PlanValidationException(BillingConstants.CAPACITY_EXTRA_STUDENTS_REQUIRED);
        }
        if (extraStudentSlots > BillingConstants.MAX_STUDENT_COUNT) {
            throw new PlanValidationException(BillingConstants.CAPACITY_EXTRA_STUDENTS_LIMIT_EXCEEDED);
        }
        if (durationDays != null) {
            throw new PlanValidationException(BillingConstants.CAPACITY_DURATION_FORBIDDEN);
        }
        if (maxStudentsPerSession != null) {
            throw new PlanValidationException(BillingConstants.CAPACITY_MAX_STUDENTS_FORBIDDEN);
        }
    }

    static void validateExamBenefits(Integer durationDays, Integer maxStudentsPerSession, Integer extraStudentSlots) {
        if (!positive(durationDays)) throw new PlanValidationException(BillingConstants.EXAM_DURATION_REQUIRED);
        if (durationDays > MAX_EXAM_DURATION_DAYS) throw new PlanValidationException(BillingConstants.EXAM_DURATION_LIMIT_EXCEEDED);
        if (!positive(maxStudentsPerSession)) throw new PlanValidationException(BillingConstants.EXAM_MAX_STUDENTS_REQUIRED);
        if (maxStudentsPerSession > BillingConstants.MAX_STUDENT_COUNT) throw new PlanValidationException(BillingConstants.EXAM_MAX_STUDENTS_LIMIT_EXCEEDED);
        if (extraStudentSlots != null) throw new PlanValidationException(BillingConstants.EXAM_EXTRA_STUDENT_SLOTS_FORBIDDEN);
    }

    private static boolean positive(Integer value) {
        return value != null && value > 0;
    }
}
