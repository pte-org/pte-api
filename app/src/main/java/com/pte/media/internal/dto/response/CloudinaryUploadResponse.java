package com.pte.media.internal.dto.response;

import java.util.UUID;

public record CloudinaryUploadResponse(
        UUID mediaPublicId,
        String publicId,
        String uploadUrl,
        String apiKey,
        String timestamp,
        String signature,
        String folder,
        String resourceType,
        long expiresInSeconds) {
}
