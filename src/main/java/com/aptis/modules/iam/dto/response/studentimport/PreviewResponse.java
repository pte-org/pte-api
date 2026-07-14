package com.aptis.modules.iam.dto.response.studentimport;

import java.util.List;

public record PreviewResponse(
        List<PreviewRow> rows,
        int totalRows,
        int errorCount,
        boolean hasErrors,
        int usernameStrategyFallbackCount) {
}
