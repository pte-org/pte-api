package com.pte.admin.dto.request;

import com.pte.admin.domain.enums.FacilityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Admin adds a branch/facility under an existing Tenant (Host). */
public record CreateOrganizationRequest(
        @NotBlank(message = "Organization name is required")
        String name,

        String address,

        @NotNull(message = "Facility type is required")
        FacilityType facilityType) {
}
