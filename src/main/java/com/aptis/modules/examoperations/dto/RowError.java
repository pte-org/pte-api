package com.aptis.modules.examoperations.dto;

/**
 * Error detail for a single invalid row in a roster import.
 */
public record RowError(
        int row,
        String field,
        String reason) {
}
