package com.pte.chat.internal.service.consent;

import java.util.UUID;

/** Throws {@code ChatConsentRequiredException} unless the student has accepted the current privacy notice. */
@FunctionalInterface
public interface ChatConsentCheck {

    void require(UUID studentId);
}
