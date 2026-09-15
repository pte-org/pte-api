package com.pte.tenancy.internal.dto.request;

import com.pte.tenancy.internal.constant.TenancyConstants;
import com.pte.tenancy.domain.enums.FacilityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Admin adds a branch/facility under an existing Tenant (Host). */
public record CreateOrganizationRequest(
        @NotBlank(message = TenancyConstants.ORGANIZATION_NAME_REQUIRED)
        String name,

        String address,

        @NotNull(message = TenancyConstants.FACILITY_TYPE_REQUIRED)
        FacilityType facilityType) {
}
