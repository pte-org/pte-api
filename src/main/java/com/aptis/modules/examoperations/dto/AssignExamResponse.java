package com.aptis.modules.examoperations.dto;

/**
 * Response for exam assignment to an import batch.
 */
public record AssignExamResponse(
        Long batchId,
        Long examId,
        String examName,
        String status) {
}
