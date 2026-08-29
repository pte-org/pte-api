package com.pte.examdelivery.client.dto;

/** exam-delivery's own view of media's presigned-download response — not a shared class. */
public record MediaPresignedDownloadResponse(String url, long expiresInSeconds) {
}
