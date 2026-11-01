package com.pte.assessment.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Per-skill redistribution suggestions for a template whose skill column no longer totals 100.
 * Nothing here is persisted; the host picks a mode (or edits by hand) and saves through the items endpoint.
 */
public record SkillWeightSuggestionResponse(List<SkillSuggestion> skills) {

    /**
     * {@code balanced} skills (total 0 with no tasks of their section, or already 100) carry no suggestion lists.
     * {@code deficit} is {@code 100 - currentTotal}, negative when the column is over 100.
     */
    public record SkillSuggestion(String skill, BigDecimal currentTotal, BigDecimal deficit, boolean balanced,
            List<TaskWeight> proportional, List<TaskWeight> equal) {
    }

    public record TaskWeight(String taskTypeKey, BigDecimal weight) {
    }
}
