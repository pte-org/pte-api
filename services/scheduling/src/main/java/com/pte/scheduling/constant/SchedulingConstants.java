package com.pte.scheduling.constant;

/** Centralized codes/labels for scheduling. */
public final class SchedulingConstants {

    public static final String SESSION_NOT_FOUND = "SESSION_NOT_FOUND";
    public static final String SNAPSHOT_NOT_FOUND = "SNAPSHOT_NOT_FOUND";
    public static final String SNAPSHOT_FETCH_FAILED = "SNAPSHOT_FETCH_FAILED";
    public static final String TASK_TYPE_NOT_IN_SNAPSHOT = "TASK_TYPE_NOT_IN_SNAPSHOT";
    public static final String EMPTY_COMPOSITION = "EMPTY_COMPOSITION";
    public static final String ALREADY_ENROLLED = "ALREADY_ENROLLED";
    public static final String ALREADY_ASSIGNED = "ALREADY_ASSIGNED";
    public static final String ENROLLMENT_NOT_FOUND = "ENROLLMENT_NOT_FOUND";
    public static final String PROCTOR_ASSIGNMENT_NOT_FOUND = "PROCTOR_ASSIGNMENT_NOT_FOUND";
    public static final String HOST_CONTEXT_REQUIRED = "HOST_CONTEXT_REQUIRED";
    public static final String INVALID_SESSION_WINDOW = "INVALID_SESSION_WINDOW";
    public static final String NOT_ENTITLED = "NOT_ENTITLED";
    public static final String PROCTOR_NOT_ASSIGNED = "PROCTOR_NOT_ASSIGNED";

    public static final String AGGREGATE_SESSION = "ExamSession";
    public static final String EVENT_SESSION_SCHEDULED = "SessionScheduled";
    public static final String EVENT_STUDENT_ENROLLED = "StudentEnrolled";
    public static final String EVENT_STUDENT_UNENROLLED = "StudentUnenrolled";
    public static final String EVENT_PROCTOR_ASSIGNED = "ProctorAssigned";
    public static final String EVENT_PROCTOR_UNASSIGNED = "ProctorUnassigned";
    public static final String EVENT_PROCTOR_ROLE_UPDATED = "ProctorRoleUpdated";
    public static final String EVENT_SCORING_REQUESTED = "ScoringRequested";
    public static final String EVENT_PUBLISH_REQUESTED = "PublishRequested";

    // RabbitMQ outbox relay (rabbitmq-outbox-migration Phase 4). Downstream
    // consumers bind their own queue to this exchange with routing key
    // "{aggregateType}.{eventType}".
    public static final String OUTBOX_EXCHANGE = "outbox.scheduling.exchange";

    // Incoming event consumed from authoring's outbox exchange (rabbitmq-outbox-migration
    // Phase 4, previously Kafka topic outbox.event.ExamSnapshot).
    public static final String INCOMING_EVENT_SNAPSHOT_PUBLISHED = "ExamSnapshotPublished";
    public static final String AUTHORING_OUTBOX_EXCHANGE = "outbox.authoring.exchange";
    public static final String QUEUE_SNAPSHOT_EVENTS = "scheduling.snapshot-events";
    public static final String QUEUE_SNAPSHOT_EVENTS_DLQ = "scheduling.snapshot-events.dlq";
    public static final String SNAPSHOT_EVENTS_ROUTING_PATTERN = "ExamSnapshot.*";
    public static final String SNAPSHOT_EVENTS_DEAD_LETTER_ROUTING_KEY = "snapshot-events";
    // AMQP replacement for the old Kafka record header of the same purpose —
    // set by AbstractOutboxRelay#publishAndConfirm when publishing, read via
    // Message.getMessageProperties().getHeaders() here.
    public static final String EVENT_TYPE_HEADER = "eventType";

    private SchedulingConstants() {
    }
}
