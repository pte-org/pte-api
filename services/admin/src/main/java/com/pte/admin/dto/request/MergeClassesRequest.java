package com.pte.admin.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** Moves every student from each source Class into the target (path variable). */
public record MergeClassesRequest(
        @NotEmpty(message = "At least one source Class is required")
        List<UUID> sourceClassPublicIds) {
}
