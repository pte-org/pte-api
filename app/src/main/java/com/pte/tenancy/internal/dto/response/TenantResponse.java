package com.pte.tenancy.internal.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID publicId,
        String code,
        String name,
        String organizationType,
        String taxCode,
        String status,
        Instant suspendedUntil,
        String packageName,
        int studentLimit,
        String logoUrl,
        String primaryColor) {
}
