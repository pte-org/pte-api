package com.pte.admin.mapper;

import com.pte.admin.domain.Tenant;
import com.pte.admin.dto.response.TenantResponse;

/** Maps the {@link Tenant} entity to its response DTO. */
public final class TenantMapper {

    private TenantMapper() {
    }

    public static TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(
                tenant.getPublicId(),
                tenant.getName(),
                tenant.getOrganizationType(),
                tenant.getStatus().name(),
                tenant.getPackageName(),
                tenant.getStudentLimit(),
                tenant.getLogoUrl(),
                tenant.getPrimaryColor());
    }
}
