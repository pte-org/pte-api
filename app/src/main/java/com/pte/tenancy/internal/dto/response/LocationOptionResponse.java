package com.pte.tenancy.internal.dto.response;

/** Stable location option exposed by the host API instead of the provider's raw payload. */
public record LocationOptionResponse(
        String code,
        String name,
        String unitType) {
}
