package com.pte.tenancy.internal.constant;

/** Error and audit action codes owned by the tenancy module. */
public final class TenancyConstants {

    public static final String TENANT_NOT_FOUND = "TENANT_NOT_FOUND";
    public static final String TENANT_NAME_ALREADY_USED = "TENANT_NAME_ALREADY_USED";
    public static final String ORGANIZATION_NOT_FOUND = "ORGANIZATION_NOT_FOUND";
    public static final String ORGANIZATION_NAME_ALREADY_USED = "ORGANIZATION_NAME_ALREADY_USED";
    public static final String QUOTA_CONFLICT = "QUOTA_CONFLICT";

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
