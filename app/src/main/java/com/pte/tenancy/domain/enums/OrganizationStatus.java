package com.pte.tenancy.domain.enums;

/**
 * Organization lifecycle: an application starts {@code PENDING}; a platform reviewer moves it to {@code ACTIVE}
 * or {@code REJECTED}; an active organization can be {@code SUSPENDED} (bounded or indefinite) and reactivated.
 */
public enum OrganizationStatus {
    PENDING,
    ACTIVE,
    REJECTED,
    SUSPENDED
}
