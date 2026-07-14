package com.aptis.modules.iam.domain.studentimport;

public record ImportRowError(
        int rowNumber,
        String errorCode) {
}
