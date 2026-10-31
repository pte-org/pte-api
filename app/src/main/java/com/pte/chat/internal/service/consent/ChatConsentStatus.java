package com.pte.chat.internal.service.consent;

import java.time.Instant;

/** {@code consentedAt} is null until the student has accepted the current version. */
public record ChatConsentStatus(String policyVersion, boolean consented, Instant consentedAt) {
}
