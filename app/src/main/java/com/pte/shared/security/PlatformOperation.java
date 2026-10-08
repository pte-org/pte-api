package com.pte.shared.security;

/**
 * Platform operations that may be delegated to {@code PLATFORM_MANAGER}.
 * Sensitive admin-only actions intentionally do not appear in this enum.
 */
public enum PlatformOperation {
    APPLICATION_READ,
    APPLICATION_REVIEW,
    TENANT_READ,
    TENANT_ONBOARD,
    TENANT_LIFECYCLE,
    TENANT_BRANDING,
    PLAN_READ_ALL,
    PLAN_DRAFT_WRITE,
    PLAN_PUBLISH,
    LICENSE_ISSUE,
    LICENSE_MASKED_READ,
    COMMERCIAL_READ,
    ANNOUNCEMENT_READ,
    ANNOUNCEMENT_DRAFT_WRITE,
    ANNOUNCEMENT_PUBLISH,
    ANNOUNCEMENT_RETRY,
    SUPPORT_READ,
    SUPPORT_MUTATE
}
