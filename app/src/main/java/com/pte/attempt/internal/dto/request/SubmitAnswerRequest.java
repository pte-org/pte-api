package com.pte.attempt.internal.dto.request;

import com.pte.attempt.internal.constant.AttemptConstants;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * {@code payload} contract by task type (opaque string, interpreted by scoring —
 * Phase 08): options-based types (e.g. {@code MC_READING_SINGLE}) — the selected
 * option's {@code orderIndex} as a decimal string ("2"); free-text types (e.g.
 * {@code WRITE_ESSAY}) — the raw response text; audio types (e.g.
 * {@code READ_ALOUD}) — the media module's {@code MediaObject.publicId} for the
 * already-uploaded (and completed) recording, NOT raw audio bytes.
 */
public record SubmitAnswerRequest(
        @NotNull(message = AttemptConstants.TASK_REFERENCE_REQUIRED) UUID pinnedItemPublicId,
        /**
         * Blank/null is a legitimate submission: resubmitted when the client's
         * local countdown hits zero with nothing answered. The answer-processing
         * path already treats a blank payload as a normal (if content-less) answer
         * end-to-end.
         */
        String payload) {
}
