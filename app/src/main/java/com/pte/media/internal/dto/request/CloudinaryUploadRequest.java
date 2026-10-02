package com.pte.media.internal.dto.request;

import com.pte.media.internal.constant.MediaConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;

public record CloudinaryUploadRequest(
        @NotBlank(message = MediaConstants.CONTENT_TYPE_REQUIRED) String contentType,
        @NotBlank String assetKind,
        @NotNull @Positive @Max(MediaConstants.MAX_SUBMISSION_BYTES) Long sizeBytes) {

    public CloudinaryUploadRequest(String contentType, String assetKind) {
        this(contentType, assetKind, 1L);
    }
}
