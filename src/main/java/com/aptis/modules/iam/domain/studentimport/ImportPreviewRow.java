package com.aptis.modules.iam.domain.studentimport;

import java.util.List;
import java.util.Map;

public record ImportPreviewRow(
        int rowNumber,
        String generatedUsername,
        String usernameBase,
        Map<String, String> fieldValues,
        String errorCode,
        List<String> warningCodes,
        boolean hasError) {
}
