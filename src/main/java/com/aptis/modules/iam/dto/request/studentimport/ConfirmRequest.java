package com.aptis.modules.iam.dto.request.studentimport;

import jakarta.validation.constraints.NotBlank;

public record ConfirmRequest(@NotBlank String importId) {
}
