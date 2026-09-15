package com.pte.enrollment.internal.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Host adds a Class (Lá»›p) under one of its own Programs. */
public record CreateClassRequest(
        @NotBlank(message = "Class name is required")
        String name) {
}
