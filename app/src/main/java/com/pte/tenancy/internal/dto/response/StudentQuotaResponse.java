package com.pte.tenancy.internal.dto.response;

import com.pte.tenancy.StudentQuota;

/** Capacity view shared by quota status and import preview endpoints. */
public record StudentQuotaResponse(
        long current,
        long limit,
        long adding,
        long remaining,
        boolean allowed) {

    public static StudentQuotaResponse from(StudentQuota quota, long adding) {
        return new StudentQuotaResponse(quota.current(), quota.limit(), adding,
                quota.remaining(), quota.canAdd(adding));
    }
}
