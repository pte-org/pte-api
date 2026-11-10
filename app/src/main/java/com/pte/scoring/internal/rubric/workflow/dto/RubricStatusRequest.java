package com.pte.scoring.internal.rubric.workflow.dto;

import com.pte.scoring.internal.constant.RubricConstants;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RubricStatusRequest(@NotNull Action action,
        @Size(max = RubricConstants.MAX_REJECTION_REASON_LENGTH) String reason,
        UUID expectedActiveVersionPublicId, @Size(max = RubricConstants.DIFF_DIGEST_LENGTH) String confirmedDiffDigest) {
    public enum Action { SUBMIT, ACTIVATE, REJECT, DISCARD }
}
