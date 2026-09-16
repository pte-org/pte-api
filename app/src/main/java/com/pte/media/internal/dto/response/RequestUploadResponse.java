package com.pte.media.internal.dto.response;

import java.util.UUID;

public record RequestUploadResponse(UUID mediaPublicId, String uploadUrl, long expiresInSeconds) {
}
