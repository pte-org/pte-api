package com.pte.tenancy.internal.constant;

import java.util.UUID;

/** Error and audit action codes owned by the tenancy module. */
public final class TenancyConstants {

    public static final String TENANT_NOT_FOUND = "TENANT_NOT_FOUND";
    public static final String TENANT_NAME_ALREADY_USED = "TENANT_NAME_ALREADY_USED";
    public static final String TENANT_CODE_ALREADY_USED = "TENANT_CODE_ALREADY_USED";
    public static final String ORGANIZATION_NOT_FOUND = "ORGANIZATION_NOT_FOUND";
    public static final String ORGANIZATION_NAME_ALREADY_USED = "ORGANIZATION_NAME_ALREADY_USED";
    public static final String QUOTA_CONFLICT = "QUOTA_CONFLICT";
    public static final String STUDENT_LIMIT_EXCEEDED =
            "STUDENT_LIMIT_EXCEEDED: current=%d, limit=%d, adding=%d";
    public static final String STUDENT_COUNT_REQUIRED = "STUDENT_COUNT_REQUIRED";
    public static final String STUDENT_COUNT_INVALID = "STUDENT_COUNT_INVALID";
    public static final String SYSTEM_QUOTA_PACKAGE = "student-capacity";
    /** Reserved scalar actor for quota grants triggered by a system activation. */
    public static final UUID SYSTEM_ACTOR_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public static final String TENANT_CODE_REQUIRED = "Tenant code is required";
    public static final String TENANT_CODE_INVALID =
            "Tenant code must be 3-32 lowercase letters, digits, or hyphens";
    public static final String ORGANIZATION_NAME_REQUIRED = "Organization name is required";
    public static final String ORGANIZATION_TYPE_REQUIRED = "Organization type is required";
    public static final String PACKAGE_NAME_REQUIRED = "Package name is required";
    public static final String STUDENT_LIMIT_REQUIRED = "Student limit is required";
    public static final String STUDENT_LIMIT_MINIMUM = "Student limit must be at least 1";
    public static final String FACILITY_TYPE_REQUIRED = "Facility type is required";
    public static final String AMOUNT_REQUIRED = "Amount is required";
    public static final String AMOUNT_POSITIVE = "Amount must be positive for a grant";
    public static final String PRIMARY_COLOR_INVALID = "Primary color must be a hex value like #1A2B3C";

    public static final String AGGREGATE_TENANT = "Tenant";
    public static final String EVENT_TENANT_ONBOARDED = "TenantOnboarded";
    public static final String EVENT_TENANT_SUSPENDED = "TenantSuspended";
    public static final String EVENT_TENANT_REACTIVATED = "TenantReactivated";
    public static final String EVENT_TENANT_BRANDING_UPDATED = "TenantBrandingUpdated";

    public static final String AGGREGATE_ORGANIZATION = "Organization";
    public static final String EVENT_ORGANIZATION_CREATED = "OrganizationCreated";
    public static final String EVENT_ORGANIZATION_SUSPENDED = "OrganizationSuspended";
    public static final String EVENT_ORGANIZATION_REACTIVATED = "OrganizationReactivated";

    public static final String AGGREGATE_QUOTA_TRANSACTION = "QuotaTransaction";
    public static final String EVENT_QUOTA_GRANTED = "QuotaGranted";

    private TenancyConstants() {
    }
}
