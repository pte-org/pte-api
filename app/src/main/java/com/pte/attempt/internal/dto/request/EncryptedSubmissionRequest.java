package com.pte.attempt.internal.dto.request;

import com.pte.attempt.internal.constant.AttemptConstants;
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
        @NotNull(message = AttemptConstants.TASK_REFERENCE_REQUIRED) UUID pinnedItemPublicId,
        @NotBlank(message = AttemptConstants.WRAPPED_KEY_REQUIRED) String wrappedKey,
        @NotBlank(message = AttemptConstants.INITIALIZATION_VECTOR_REQUIRED) String iv,
        @NotBlank(message = AttemptConstants.CIPHERTEXT_REQUIRED) String ciphertext) {
}
