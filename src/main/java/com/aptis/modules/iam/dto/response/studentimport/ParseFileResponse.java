package com.aptis.modules.iam.dto.response.studentimport;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ParseFileResponse(
        String importId,
        List<String> columnHeaders,
        List<Map<String, String>> sampleRows,
        int estimatedRowCount,
        LocalDateTime expiresAt) {
}
