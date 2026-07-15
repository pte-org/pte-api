package com.aptis.modules.questionbank.dto.response;

public record QuestionImportRowError(
        int rowNumber,
        String field,
        String message) {
}
