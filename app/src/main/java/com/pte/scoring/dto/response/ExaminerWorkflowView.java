package com.pte.scoring.dto.response;

import com.pte.scoring.domain.enums.ExaminerWorkflowStatus;
import java.time.Instant;
import java.util.List;

public record ExaminerWorkflowView(
        String mode,
        ExaminerWorkflowStatus status,
        long workVersion,
        Instant startedAt,
        Instant finalizedAt,
        boolean readOnly,
        boolean canStart,
        boolean canSaveDraft,
        boolean canFinalize,
        String blockingReason,
        List<ExaminerDraftResponse> drafts
) {}
