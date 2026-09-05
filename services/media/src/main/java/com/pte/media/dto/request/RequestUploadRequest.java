package com.pte.media.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code audioPrompt} is an opt-in signal — {@code null}/absent (every caller
 * before this field existed: candidates' own recorded answers, any other use
 * of this generic endpoint) keeps today's unrestricted behavior exactly as
 * before. Only a caller uploading a Speaking task's audio prompt sets this
 * {@code true}, which narrows the accepted {@code contentType} to WAV-only
 * and triggers duration extraction at complete-upload time
 * (plans/phat-speaking-dynamic-prep-timing).
 */
public record RequestUploadRequest(
        @NotBlank(message = "Content type is required") String contentType,
        Boolean audioPrompt) {
}
