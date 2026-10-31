package com.pte.scoring.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** The Examiner who takes over one committed attempt. */
public record ReassignExaminerRequest(@NotNull UUID examinerPublicId) {
}
