package com.pte.admin.dto.response;

import java.util.UUID;

public record OrganizationResponse(
        UUID publicId,
        UUID tenantPublicId,
        String name,
        String address,
        String facilityType,
        String status) {
}
