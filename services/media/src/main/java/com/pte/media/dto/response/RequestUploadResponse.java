package com.pte.media.dto.response;

import java.util.UUID;

public record RequestUploadResponse(UUID mediaPublicId, String uploadUrl, long expiresInSeconds) {
}
