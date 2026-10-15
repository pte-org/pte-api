package com.pte.tenancy.internal.dto.request;

import com.pte.tenancy.domain.enums.FacilityType;
import com.pte.tenancy.internal.constant.TenancyConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Fields a Host administrator may update on the tenant's organization profile. */
public record UpdateOrganizationProfileRequest(
        @NotBlank(message = TenancyConstants.ORGANIZATION_NAME_REQUIRED)
        @Size(max = 255, message = TenancyConstants.ORGANIZATION_NAME_TOO_LONG)
        String name,
        @Size(max = 255, message = TenancyConstants.ORGANIZATION_ADDRESS_TOO_LONG)
        String address,
        @Size(max = 16, message = TenancyConstants.ORGANIZATION_LOCATION_CODE_TOO_LONG)
        String provinceCode,
        @Size(max = 16, message = TenancyConstants.ORGANIZATION_LOCATION_CODE_TOO_LONG)
        String wardCode,
        @NotNull(message = TenancyConstants.FACILITY_TYPE_REQUIRED)
        FacilityType facilityType) {

    /** Compatibility constructor for callers that do not send structured location fields yet. */
    public UpdateOrganizationProfileRequest(String name, String address, FacilityType facilityType) {
        this(name, address, null, null, facilityType);
    }
}
