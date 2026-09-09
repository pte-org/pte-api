package com.pte.admin.constant;

/** Centralized codes/labels for the admin control plane. */
public final class AdminConstants {

    public static final String TENANT_NOT_FOUND = "TENANT_NOT_FOUND";
    public static final String TENANT_NAME_ALREADY_USED = "TENANT_NAME_ALREADY_USED";
    public static final String ORGANIZATION_NOT_FOUND = "ORGANIZATION_NOT_FOUND";
    public static final String ORGANIZATION_NAME_ALREADY_USED = "ORGANIZATION_NAME_ALREADY_USED";
    public static final String QUOTA_CONFLICT = "QUOTA_CONFLICT";
    public static final String PROGRAM_NOT_FOUND = "PROGRAM_NOT_FOUND";
    public static final String PROGRAM_NAME_ALREADY_USED = "PROGRAM_NAME_ALREADY_USED";
    public static final String PROGRAM_HAS_ACTIVE_CLASSES = "PROGRAM_HAS_ACTIVE_CLASSES";
    public static final String CLASS_NOT_FOUND = "CLASS_NOT_FOUND";
    public static final String CLASS_NAME_ALREADY_USED = "CLASS_NAME_ALREADY_USED";
    public static final String CLASS_HAS_ACTIVE_MEMBERS = "CLASS_HAS_ACTIVE_MEMBERS";
    public static final String STUDENT_ALREADY_IN_CLASS = "STUDENT_ALREADY_IN_CLASS";
    public static final String CLASS_MEMBERSHIP_NOT_FOUND = "CLASS_MEMBERSHIP_NOT_FOUND";
    public static final String LECTURER_ALREADY_ASSIGNED = "LECTURER_ALREADY_ASSIGNED";
    public static final String LECTURER_ASSIGNMENT_NOT_FOUND = "LECTURER_ASSIGNMENT_NOT_FOUND";
    public static final String COORDINATOR_ALREADY_ASSIGNED = "COORDINATOR_ALREADY_ASSIGNED";
    public static final String COORDINATOR_ASSIGNMENT_NOT_FOUND = "COORDINATOR_ASSIGNMENT_NOT_FOUND";

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

    public static final String AGGREGATE_PROGRAM = "Program";
    public static final String EVENT_PROGRAM_CREATED = "ProgramCreated";
    public static final String EVENT_PROGRAM_UPDATED = "ProgramUpdated";
    public static final String EVENT_PROGRAM_STATUS_CHANGED = "ProgramStatusChanged";
    public static final String EVENT_PROGRAM_ARCHIVED = "ProgramArchived";

    public static final String AGGREGATE_CLASS = "StudentClass";
    public static final String EVENT_CLASS_CREATED = "ClassCreated";
    public static final String EVENT_CLASS_UPDATED = "ClassUpdated";
    public static final String EVENT_CLASS_STATUS_CHANGED = "ClassStatusChanged";
    public static final String EVENT_CLASS_ARCHIVED = "ClassArchived";
    public static final String EVENT_STUDENT_ASSIGNED_TO_CLASS = "StudentAssignedToClass";
    public static final String EVENT_STUDENT_UNASSIGNED_FROM_CLASS = "StudentUnassignedFromClass";
    public static final String EVENT_STUDENT_TRANSFERRED_CLASS = "StudentTransferredClass";
    public static final String EVENT_LECTURER_ASSIGNED = "LecturerAssigned";
    public static final String EVENT_LECTURER_UNASSIGNED = "LecturerUnassigned";
    public static final String EVENT_COORDINATOR_ASSIGNED = "CoordinatorAssigned";
    public static final String EVENT_COORDINATOR_UNASSIGNED = "CoordinatorUnassigned";

    // RabbitMQ outbox relay (rabbitmq-outbox-migration Phase 2). Downstream
    // consumers (e.g. iam's TenantEventConsumer, Phase 3) bind their own
    // queue to this exchange with routing key "{aggregateType}.{eventType}".
    public static final String OUTBOX_EXCHANGE = "outbox.admin.exchange";

    private AdminConstants() {
    }
}
