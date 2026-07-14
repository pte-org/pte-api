package com.aptis.modules.examdelivery.dto;

public record SpeakingUploadResponse(
    Long questionId,
    String audioUrl,
    Long fileSize,
    String mimeType
) {}
