package com.pte.admin.constant;

/** Centralized codes/labels for the admin control plane. */
public final class AdminConstants {

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

    // RabbitMQ outbox relay (rabbitmq-outbox-migration Phase 2). Downstream
    // consumers (e.g. iam's TenantEventConsumer, Phase 3) bind their own
    // queue to this exchange with routing key "{aggregateType}.{eventType}".
    public static final String OUTBOX_EXCHANGE = "outbox.admin.exchange";

    private AdminConstants() {
    }
}
