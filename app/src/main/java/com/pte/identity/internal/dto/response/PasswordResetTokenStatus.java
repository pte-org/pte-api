package com.pte.identity.internal.dto.response;

/** Whether a reset link can still be used. It never says why not: spent, expired and unknown look the same. */
public record PasswordResetTokenStatus(boolean valid) {
}
