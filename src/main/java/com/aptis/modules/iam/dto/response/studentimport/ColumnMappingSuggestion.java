package com.aptis.modules.iam.dto.response.studentimport;

public record ColumnMappingSuggestion(
        String columnName,
        String suggestedField,
        String confidence) {
}
