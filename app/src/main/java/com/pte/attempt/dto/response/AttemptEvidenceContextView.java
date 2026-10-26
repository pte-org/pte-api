package com.pte.attempt.dto.response;

import java.util.UUID;

/** The facts about an attempt that student security evidence needs: whose it is, where, and its lockdown policy. */
public record AttemptEvidenceContextView(UUID attemptPublicId, UUID sessionPublicId, UUID studentPublicId,
                                         UUID tenantId, String lockdownMode) {
}
