package com.pte.iam.domain.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * iam's own view of admin's {@code TenantOnboarded} payload — only the field
 * iam actually needs. {@code ignoreUnknown} because admin's payload carries
 * more fields (name, organizationType) that iam doesn't care about (§3: never
 * share producer/consumer DTOs across services).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TenantOnboardedEvent(UUID tenantPublicId) {
}
