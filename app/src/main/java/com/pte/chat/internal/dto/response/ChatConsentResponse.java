package com.pte.chat.internal.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** {@code consentedAt} is left out until the student has accepted the current version. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatConsentResponse(String policyVersion, boolean consented, Instant consentedAt) {
}
