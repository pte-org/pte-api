package com.pte.admin.constant;

/** Centralized codes/labels for the admin control plane. */
public final class AdminConstants {

    public static final String TENANT_NOT_FOUND = "TENANT_NOT_FOUND";
    public static final String TENANT_NAME_ALREADY_USED = "TENANT_NAME_ALREADY_USED";

    public static final String AGGREGATE_TENANT = "Tenant";
    public static final String EVENT_TENANT_ONBOARDED = "TenantOnboarded";
    public static final String EVENT_TENANT_SUSPENDED = "TenantSuspended";

    // RabbitMQ outbox relay (rabbitmq-outbox-migration Phase 2). Downstream
    // consumers (e.g. iam's TenantEventConsumer, Phase 3) bind their own
    // queue to this exchange with routing key "{aggregateType}.{eventType}".
    public static final String OUTBOX_EXCHANGE = "outbox.admin.exchange";

    private AdminConstants() {
    }
}
