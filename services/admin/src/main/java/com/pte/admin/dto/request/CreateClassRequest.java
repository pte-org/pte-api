package com.pte.admin.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Host adds a Class (Lớp) under one of its own Programs. */
public record CreateClassRequest(
        @NotBlank(message = "Class name is required")
        String name) {
}
