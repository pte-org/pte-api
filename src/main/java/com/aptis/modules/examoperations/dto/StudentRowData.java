package com.aptis.modules.examoperations.dto;

/**
 * Parsed row data from Excel roster — input for student provisioning.
 * Produced by FS2-04/05 (parsing), consumed by FS2-06 (provisioning).
 */
public record StudentRowData(
        int rowNumber,
        String fullName,
        String studentCode,
        String className,
        String email,
        String phone) {
}
