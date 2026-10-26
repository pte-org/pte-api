package com.pte.tenancy.internal.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * One organization as seen by platform operators (governance fields) and by its Host (profile fields). The
 * {@code publicId} is the identifier other modules call {@code tenantId}.
 */
public record OrganizationResponse(
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
        String primaryColor,
        String address,
        String provinceCode,
        String provinceName,
        String wardCode,
        String wardName,
        String facilityType) {
}
