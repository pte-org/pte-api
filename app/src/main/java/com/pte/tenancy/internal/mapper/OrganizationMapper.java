package com.pte.tenancy.internal.mapper;

import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.internal.dto.response.OrganizationResponse;

/** Maps the {@link Organization} entity to its response DTO. */
public final class OrganizationMapper {

    private OrganizationMapper() {
    }

    public static OrganizationResponse toResponse(Organization organization) {
        return new OrganizationResponse(
                organization.getPublicId(),
                organization.getCode(),
                organization.getName(),
                organization.getOrganizationType(),
                organization.getTaxCode(),
                organization.getStatus().name(),
                organization.getSuspendedUntil(),
                organization.getPackageName(),
                organization.getStudentLimit(),
                organization.getLogoUrl(),
                organization.getPrimaryColor(),
                organization.getAddress(),
                organization.getProvinceCode(),
                organization.getProvinceName(),
                organization.getWardCode(),
                organization.getWardName(),
                organization.getFacilityType().name());
    }
}
