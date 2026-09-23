package com.pte.scoring.dto.request;

import com.pte.scoring.domain.enums.AssignmentBatchMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Manual entries carry one Examiner per scope; random mode supplies the Examiner pool separately. */
public record CreateExaminerAssignmentPreviewRequest(
        @NotNull AssignmentBatchMode mode,
        @NotEmpty @Size(max = 100) List<@Valid AssignmentScopeRequest> scopes,
        @Size(max = 50) List<@NotNull UUID> examinerPublicIds) {

    public record AssignmentScopeRequest(
            @NotNull com.pte.scoring.domain.enums.AssignmentScopeType type,
            @NotNull UUID scopePublicId,
            UUID examinerPublicId) {
    }
}
