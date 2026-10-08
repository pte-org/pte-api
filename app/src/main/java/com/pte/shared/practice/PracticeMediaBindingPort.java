package com.pte.shared.practice;

import com.pte.shared.security.CurrentUser;

import java.util.UUID;

/**
 * Cross-module authorization port for media owned by a practice session.
 *
 * <p>The implementation belongs to the practice module. Keeping the port in
 * {@code shared} prevents the media adapter from depending on practice while
 * still letting practice enforce its own session and item lifecycle rules.
 */
public interface PracticeMediaBindingPort {

    void assertCanUseResponseAudio(UUID sessionPublicId, UUID itemPublicId, CurrentUser caller);
}
