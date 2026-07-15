package com.aptis.modules.questionbank.dto.response;

import java.util.List;

public record QuestionImportResultResponse(
        int importedCount,
        int skippedCount,
        List<QuestionImportRowError> errorDetails) {
}
