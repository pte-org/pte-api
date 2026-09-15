package com.pte.iam.controller.dto;

import java.time.Instant;
import java.util.UUID;

/** Safe, rebuild-only representation of an IAM student. */
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
