package com.pte.billing.internal.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Safe issuance acknowledgement. Bearer code retrieval is a separate operation. */
public record LicenseIssueReceipt(UUID publicId, UUID planId, String persistedStatus, String status,
        Instant issuedAt, Instant codeExpiresAt, boolean replayed) {}
