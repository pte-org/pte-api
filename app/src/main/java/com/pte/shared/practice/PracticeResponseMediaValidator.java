package com.pte.shared.practice;

import com.pte.shared.security.CurrentUser;

import java.util.UUID;

/**
 * Cross-module port for validating an uploaded practice response recording.
 * The media module owns storage/provider validation; practice owns when this
 * validation is required in its answer lifecycle.
 */
public interface PracticeResponseMediaValidator {

    void validatePracticeResponseAudio(UUID mediaPublicId, UUID sessionPublicId,
            UUID itemPublicId, CurrentUser caller);
}
