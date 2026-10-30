package com.pte.assessment.domain.enums;

/**
 * Lifecycle of a {@code ScoreTemplate}. One-way transitions only:
 * DRAFT -> ACTIVE -> RETIRED. A DRAFT is freely editable; ACTIVE/RETIRED are
 * immutable once reached (enforced in {@code ScoreTemplateAdminService}, not
 * by the DB).
 */
public enum ExamDefinitionStatus {
    DRAFT,
    PENDING_APPROVAL,
    ACTIVE,
    RETIRED
}
