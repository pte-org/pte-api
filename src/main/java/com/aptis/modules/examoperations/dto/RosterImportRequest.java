package com.aptis.modules.examoperations.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * Request body for POST /api/v1/host/imports.
 * Wraps batch metadata + list of valid parsed rows from the Excel file.
 */
public record RosterImportRequest(
        @NotBlank(message = "Filename is required")
        String filename,

        @NotEmpty(message = "At least one valid row is required")
        @Valid
        List<StudentRowData> validRows,

        List<RowError> errors) {
}
