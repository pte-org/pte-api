package com.pte.tenancy.internal.dto.request;

import com.pte.tenancy.internal.constant.TenancyConstants;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Admin onboards an organization onto the platform. */
public record OnboardTenantRequest(
        @NotBlank(message = TenancyConstants.TENANT_CODE_REQUIRED)
        @Pattern(regexp = "^[a-z0-9-]{3,32}$", message = TenancyConstants.TENANT_CODE_INVALID)
        String code,

        @NotBlank(message = TenancyConstants.ORGANIZATION_NAME_REQUIRED)
        String name,

        @NotBlank(message = TenancyConstants.ORGANIZATION_TYPE_REQUIRED)
        String organizationType,

        @NotBlank(message = TenancyConstants.TAX_CODE_REQUIRED)
        @Size(max = 64, message = TenancyConstants.TAX_CODE_MAX)
        String taxCode,

        @NotBlank(message = TenancyConstants.PACKAGE_NAME_REQUIRED)
        String packageName,

        @NotNull(message = TenancyConstants.STUDENT_LIMIT_REQUIRED)
        @Min(value = 1, message = TenancyConstants.STUDENT_LIMIT_MINIMUM)
        Integer studentLimit) {
}
