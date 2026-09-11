package com.pte.admin.mapper;

import com.pte.admin.domain.Organization;
import com.pte.admin.dto.response.OrganizationResponse;

import java.util.UUID;

/**
 * Maps {@link Organization} to its response DTO. Takes {@code tenantPublicId}
 * explicitly rather than reading it off {@code organization.getTenant()} —
 * callers already know it (from the request path or the just-saved parent),
 * so this avoids ever lazy-loading the {@code tenant} association per row.
 */
public final class OrganizationMapper {

    private OrganizationMapper() {
    }

    public static OrganizationResponse toResponse(Organization organization, UUID tenantPublicId) {
        return new OrganizationResponse(
                organization.getPublicId(),
                tenantPublicId,
                organization.getName(),
                organization.getAddress(),
                organization.getFacilityType().name(),
                organization.getStatus().name());
    }
}
