package com.pte.media.internal.dto.request;

import com.pte.media.internal.constant.MediaConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record CloudinaryUploadRequest(
        @NotBlank(message = MediaConstants.CONTENT_TYPE_REQUIRED) String contentType,
        @NotBlank String assetKind,
        @NotNull @Positive @Max(MediaConstants.MAX_SUBMISSION_BYTES) Long sizeBytes,
        UUID practiceSessionId,
        UUID practiceItemId,
        String purpose,
        /** Pool of the question a prompt file is for; picks its folder. Missing means EXAM. */
        @Pattern(regexp = MediaConstants.POOL_EXAM + "|" + MediaConstants.POOL_PRACTICE,
                message = MediaConstants.QUESTION_POOL_INVALID) String pool) {

    /** A practice recording or an exam answer: no question pool. */
    public CloudinaryUploadRequest(String contentType, String assetKind, Long sizeBytes, UUID practiceSessionId,
            UUID practiceItemId, String purpose) {
        this(contentType, assetKind, sizeBytes, practiceSessionId, practiceItemId, purpose, null);
    }

    public CloudinaryUploadRequest(String contentType, String assetKind, Long sizeBytes) {
        this(contentType, assetKind, sizeBytes, null, null, null);
    }

    public CloudinaryUploadRequest(String contentType, String assetKind) {
        this(contentType, assetKind, 1L, null, null, null);
    }
}
