package com.pte.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** Creates a new Class under the source's own Program (path variable), then moves the given subset into it. */
public record SplitClassRequest(
        @NotBlank(message = "New Class name is required") String newClassName,
        @NotEmpty(message = "At least one student is required") List<UUID> studentPublicIds) {
}
