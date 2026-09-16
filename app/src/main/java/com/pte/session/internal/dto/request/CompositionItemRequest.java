package com.pte.session.internal.dto.request;

import com.pte.session.internal.constant.SessionConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CompositionItemRequest(
        @NotBlank(message = SessionConstants.TASK_TYPE_REQUIRED) String taskType,
        @NotBlank(message = SessionConstants.SECTION_REQUIRED) String section,
        int orderIndex,
        @Positive(message = SessionConstants.TIMING_OVERRIDE_POSITIVE) Integer timingOverrideSeconds,
        /** Null = inherit the session's {@code ExamPolicy.replayPolicy}; non-null always wins over it. */
        @Positive(message = SessionConstants.MAX_PLAY_COUNT_POSITIVE) Integer maxPlayCount) {
}
