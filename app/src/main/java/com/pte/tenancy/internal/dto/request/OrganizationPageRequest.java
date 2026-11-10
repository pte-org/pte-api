package com.pte.tenancy.internal.dto.request;

import com.pte.tenancy.internal.constant.TenancyConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Bounded platform organization lookup; omitted parameters open the first page. */
public record OrganizationPageRequest(
        @Min(value = 0, message = TenancyConstants.ORGANIZATION_PAGE_INVALID) Integer page,
        @Min(value = 1, message = TenancyConstants.ORGANIZATION_PAGE_SIZE_INVALID)
        @Max(value = TenancyConstants.ORGANIZATION_PAGE_MAX_SIZE,
                message = TenancyConstants.ORGANIZATION_PAGE_SIZE_INVALID) Integer size,
        @Size(max = TenancyConstants.ORGANIZATION_SEARCH_MAX_LENGTH,
                message = TenancyConstants.ORGANIZATION_SEARCH_TOO_LONG) String q) {

    public OrganizationPageRequest {
        page = page == null ? 0 : page;
        size = size == null ? TenancyConstants.ORGANIZATION_PAGE_DEFAULT_SIZE : size;
    }
}
