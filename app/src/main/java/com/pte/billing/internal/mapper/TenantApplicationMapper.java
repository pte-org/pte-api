package com.pte.billing.internal.mapper;

import com.pte.billing.domain.enums.TenantApplicationStatus;
import com.pte.billing.internal.dto.response.TenantApplicationResponse;
import com.pte.tenancy.domain.Organization;

/** Presents an organization that went through the onboarding application as an application record. */
public final class TenantApplicationMapper {

    private TenantApplicationMapper() {
    }

    public static TenantApplicationResponse toResponse(Organization application) {
        return new TenantApplicationResponse(
                application.getPublicId(),
                application.getName(),
                application.getOrganizationType(),
                application.getCode(),
                application.getContactEmail(),
                application.getContactPhone(),
                application.getTaxCode(),
                statusOf(application).name(),
                application.getReviewedBy(),
                application.getReviewedAt(),
                application.getRejectReason());
    }

    /** ACTIVE and SUSPENDED organizations were both approved applications. */
    public static TenantApplicationStatus statusOf(Organization application) {
        if (application.isPending()) {
            return TenantApplicationStatus.PENDING;
        }
        return application.isRejected() ? TenantApplicationStatus.REJECTED : TenantApplicationStatus.APPROVED;
    }
}
