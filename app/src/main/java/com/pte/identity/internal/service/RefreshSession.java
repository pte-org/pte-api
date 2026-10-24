package com.pte.identity.internal.service;

import java.util.UUID;

/** What a consumed refresh token proves: the account and the organization it was issued for (null if none). */
public record RefreshSession(Long userId, UUID organizationId) {
}
