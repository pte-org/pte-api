package com.aptis.modules.examoperations.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /api/v1/host/imports/{batchId}/assign.
 */
public record AssignExamRequest(
        @NotNull(message = "Exam ID is required")
        Long examId) {
}
