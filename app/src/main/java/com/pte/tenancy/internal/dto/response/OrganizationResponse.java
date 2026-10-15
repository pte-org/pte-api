package com.pte.tenancy.internal.dto.response;

import java.util.UUID;

public record OrganizationResponse(
        UUID publicId,
        UUID tenantPublicId,
        String name,
        String address,
        String provinceCode,
        String provinceName,
        String wardCode,
        String wardName,
        String facilityType,
        String status) {
}
