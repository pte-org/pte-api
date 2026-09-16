package com.pte.media.internal.dto.request;

import com.pte.media.internal.constant.MediaConstants;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code audioPrompt} is an opt-in signal — {@code null}/absent (candidates'
 * own recorded answers, any other use of this generic endpoint) keeps the
 * unrestricted behavior. Only a caller uploading a Speaking task's audio
 * prompt sets this {@code true}, which narrows the accepted
 * {@code contentType} to WAV-only and triggers duration extraction at
 * complete-upload time.
 */
public record RequestUploadRequest(
        @NotBlank(message = MediaConstants.CONTENT_TYPE_REQUIRED) String contentType,
        Boolean audioPrompt) {
}
