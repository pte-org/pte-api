package com.pte.media.internal.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CloudinaryCompleteRequest(
        @NotBlank String publicId,
        @NotBlank String assetId,
        @NotBlank String secureUrl,
        @NotBlank String resourceType,
        String format,
        Long bytes,
        Integer durationSeconds,
        Long version,
        String signature) {
}
