package com.pte.iam.domain.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TenantReactivatedEvent(UUID tenantPublicId) {
}
