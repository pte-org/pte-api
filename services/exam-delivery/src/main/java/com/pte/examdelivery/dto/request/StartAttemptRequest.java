package com.pte.examdelivery.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StartAttemptRequest(@NotNull(message = "Session reference is required") UUID sessionPublicId) {
}
