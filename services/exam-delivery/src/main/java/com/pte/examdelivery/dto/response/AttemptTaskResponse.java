package com.pte.examdelivery.dto.response;

import java.util.UUID;

/**
 * {@code completed=true} once every task is done/expired; {@code task} is null in that case.
 * {@code encryptionPublicKey} (Base64 X.509 SubjectPublicKeyInfo) is populated only on the
 * {@code startAttempt} response for a STRICT-pinned {@code answerIntegrityLevel}; null everywhere
 * else, including STANDARD-pinned attempts and every other response built off this same record.
 */
public record AttemptTaskResponse(UUID attemptPublicId, String attemptStatus, boolean completed, TaskView task,
        String encryptionPublicKey) {
}
