package com.pte.media.dto.response;

public record PresignedDownloadResponse(String url, long expiresInSeconds) {
}
