package com.pte.admin.domain.enums;

/**
 * Independent from {@link TenantStatus}: an Organization (branch/facility)
 * can be suspended on its own without affecting its parent Tenant or
 * siblings — a deliberately separate lifecycle, not a shared enum.
 */
public enum OrganizationStatus {
    ACTIVE,
    SUSPENDED
}
