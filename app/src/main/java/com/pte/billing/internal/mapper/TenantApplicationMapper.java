package com.pte.billing.internal.mapper;

import com.pte.billing.domain.TenantApplication;
import com.pte.billing.internal.dto.response.TenantApplicationResponse;

public final class TenantApplicationMapper {

    private TenantApplicationMapper() {
    }

    public static TenantApplicationResponse toResponse(TenantApplication application) {
        return new TenantApplicationResponse(
                application.getPublicId(),
                application.getOrgName(),
                application.getOrgType(),
                application.getRequestedCode(),
                application.getContactEmail(),
                application.getContactPhone(),
                application.getTaxCode(),
                application.getStatus().name(),
                application.getReviewedBy(),
                application.getReviewedAt(),
                application.getRejectReason());
    }
}
