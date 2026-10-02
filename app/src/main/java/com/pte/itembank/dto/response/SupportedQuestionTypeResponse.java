package com.pte.itembank.dto.response;

import com.pte.itembank.domain.enums.PteSection;

/**
 * A standard task code that can be added to the persisted question-type catalog.
 *
 * <p>This is compatibility metadata from the question-bank domain enum, not a
 * catalog row. The question-type list used by authoring and templates remains
 * the persisted catalog returned by {@code QuestionTypeResponse}.
 */
public record SupportedQuestionTypeResponse(
        String code,
        PteSection section,
        boolean scored) {
}
