package com.pte.admin.client.dto;

import java.time.Instant;
import java.util.UUID;

/** Admin-local view of IAM's safe student export contract. */
public record StudentExportItem(
        UUID studentPublicId,
        UUID tenantId,
        String email,
        String fullName,
        String studentCode,
        String phone,
        String status,
        Instant createdAt) {
}
