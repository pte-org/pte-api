package com.aptis.modules.examoperations.dto;

import java.util.List;

/**
 * Response for roster import — provisioning report.
 */
public record RosterImportResponse(
        Long batchId,
        int validCount,
        int errorCount,
        List<RowError> errors) {
}
