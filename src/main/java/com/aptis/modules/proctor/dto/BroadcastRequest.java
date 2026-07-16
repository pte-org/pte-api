package com.aptis.modules.proctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Message is always treated as plaintext — no HTML/Markdown parsing (Design Constraint). */
public record BroadcastRequest(
        @NotBlank(message = "message is required")
        @Size(max = 1000, message = "message must not exceed 1000 characters")
        String message) {
}
