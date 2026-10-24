package com.pte.tenancy.internal.mapper;

import com.pte.tenancy.domain.QuotaGrant;
import com.pte.tenancy.internal.dto.response.QuotaGrantResponse;

import java.util.UUID;

public final class QuotaGrantMapper {

    private QuotaGrantMapper() {
    }

    public static QuotaGrantResponse toResponse(QuotaGrant grant, UUID organizationPublicId) {
        return new QuotaGrantResponse(
                grant.getPublicId(),
                organizationPublicId,
                grant.getPackageName(),
                grant.getAmount(),
                grant.getActorUserId(),
                grant.getNote(),
                grant.getCreatedAt());
    }
}
