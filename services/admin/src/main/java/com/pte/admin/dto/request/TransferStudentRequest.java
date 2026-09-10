package com.pte.admin.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Moves a student's existing membership to a different Class — may belong to a different Program. */
public record TransferStudentRequest(
        @NotNull(message = "Target class reference is required")
        UUID targetClassPublicId) {
}
