package com.pte.media.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RequestUploadRequest(@NotBlank(message = "Content type is required") String contentType) {
}
