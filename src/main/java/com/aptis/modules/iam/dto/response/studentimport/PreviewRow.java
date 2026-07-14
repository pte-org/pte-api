package com.aptis.modules.iam.dto.response.studentimport;

import java.util.List;
import java.util.Map;

public record PreviewRow(
        int rowNumber,
        String generatedUsername,
        String usernameBase,
        Map<String, String> fieldValues,
        String errorCode,
        List<String> warningCodes,
        boolean hasError) {
}
