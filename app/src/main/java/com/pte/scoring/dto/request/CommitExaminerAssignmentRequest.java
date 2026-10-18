package com.pte.scoring.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** The Host's final attempt-to-Examiner table; it must cover every eligible attempt of the session. */
public record CommitExaminerAssignmentRequest(
        @NotEmpty @Size(max = 5000) List<@Valid Entry> entries) {

    public record Entry(@NotNull UUID attemptPublicId, @NotNull UUID examinerPublicId) {
    }
}
