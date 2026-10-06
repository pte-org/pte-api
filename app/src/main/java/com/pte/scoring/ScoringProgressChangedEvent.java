package com.pte.scoring;

import java.util.UUID;

/** Internal scoring wake-up; reconciliation remains periodic for crash recovery. */
public record ScoringProgressChangedEvent(UUID tenantPublicId, UUID sessionPublicId) {
}
