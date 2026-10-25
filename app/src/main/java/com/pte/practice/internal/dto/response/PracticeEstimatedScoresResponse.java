package com.pte.practice.internal.dto.response;

/**
 * Reference-only 10-90 estimate of a full mock test, built from the item scores with the Score Template weights.
 * A skill is null while none of its weighted task types has a score. {@code skippedItemCount} items (skipped or never
 * answered) count as zero; {@code excludedItemCount} items could not be scored and are left out.
 */
public record PracticeEstimatedScoresResponse(
        Integer overall,
        Integer speaking,
        Integer writing,
        Integer reading,
        Integer listening,
        int skippedItemCount,
        int excludedItemCount,
        boolean pending) {
}
