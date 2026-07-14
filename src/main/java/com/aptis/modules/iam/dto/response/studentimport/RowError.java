package com.aptis.modules.iam.dto.response.studentimport;

public record RowError(
        int rowNumber,
        String errorCode) {
}
