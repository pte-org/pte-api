package com.pte.examdelivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * STRICT-integrity counterpart to {@link SubmitAnswerRequest}'s plain {@code payload} — used only
 * when the attempt's pinned {@code answerIntegrityLevel == STRICT}. {@code wrappedKey} is the
 * per-submission AES-256 key wrapped with the server's RSA public key (RSA-OAEP, SHA-256/MGF1-SHA256);
 * {@code iv} is the 96-bit AES-GCM IV; {@code ciphertext} is the AES-GCM output with its 128-bit
 * auth tag appended. All three Base64-encoded.
 */
public record EncryptedSubmissionRequest(
        @NotNull(message = "Task reference is required") UUID pinnedItemPublicId,
        @NotBlank(message = "Wrapped key is required") String wrappedKey,
        @NotBlank(message = "Initialization vector is required") String iv,
        @NotBlank(message = "Ciphertext is required") String ciphertext) {
}
