package com.pte.media.internal.dto.response;

public record MediaPreviewResponse(String url, long expiresInSeconds, Integer durationSeconds) {
}
