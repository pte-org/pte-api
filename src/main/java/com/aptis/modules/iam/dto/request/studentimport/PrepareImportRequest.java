package com.aptis.modules.iam.dto.request.studentimport;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record PrepareImportRequest(
        @NotBlank String fileName,
        @NotEmpty List<String> columnHeaders,
        @NotEmpty List<Map<String, String>> rows) {
}
