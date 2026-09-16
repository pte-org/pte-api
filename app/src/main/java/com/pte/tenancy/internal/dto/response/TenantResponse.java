package com.pte.tenancy.internal.dto.response;

import java.util.UUID;

public record TenantResponse(
        UUID publicId,
        String code,
        String name,
        String organizationType,
        String status,
        String packageName,
        int studentLimit,
        String logoUrl,
        String primaryColor) {
}
