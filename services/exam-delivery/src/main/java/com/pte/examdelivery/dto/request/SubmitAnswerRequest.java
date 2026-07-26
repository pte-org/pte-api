package com.pte.examdelivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * {@code payload} contract by task type (opaque string, interpreted by scoring
 * — Phase 7/9): options-based types (e.g. {@code MC_READING_SINGLE}) — the
 * selected option's {@code orderIndex} as a decimal string ("2"); free-text
 * types (e.g. {@code WRITE_ESSAY}) — the raw response text; audio types (e.g.
 * {@code READ_ALOUD}) — the media service's {@code MediaObject.publicId} for
 * the already-uploaded (and completed) recording, NOT raw audio bytes — the
 * student uploads to media directly via its presigned URL first, then submits
 * this reference (ADR-003: binary never flows through the transactional API tier).
 */
public record SubmitAnswerRequest(
        @NotNull(message = "Task reference is required") UUID pinnedItemPublicId,
        @NotBlank(message = "Answer payload is required") String payload) {
}
