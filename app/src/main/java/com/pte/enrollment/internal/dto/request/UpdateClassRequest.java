package com.pte.enrollment.internal.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateClassRequest(
        @NotBlank(message = "Class name is required")
        String name) {
}
