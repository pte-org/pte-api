package com.pte.notification.constant;

/** Centralized codes/labels for notification. */
public final class NotificationConstants {

    // AMQP replacement for the old Kafka record header of the same purpose —
    // set by AbstractOutboxRelay#publishAndConfirm when publishing, read via
    // Message.getMessageProperties().getHeaders() here.
    public static final String EVENT_TYPE_HEADER = "eventType";

    // Incoming: iam's outbox exchange (rabbitmq-outbox-migration Phase 8,
    // previously Kafka topic outbox.event.User). Not ordering-sensitive.
    public static final String IAM_OUTBOX_EXCHANGE = "outbox.iam.exchange";
    public static final String QUEUE_USER_EVENTS = "notification.user-events";
    public static final String QUEUE_USER_EVENTS_DLQ = "notification.user-events.dlq";
    public static final String USER_EVENTS_ROUTING_PATTERN = "User.*";
    public static final String USER_EVENTS_DEAD_LETTER_ROUTING_KEY = "user-events";
    public static final String INCOMING_EVENT_USER_CREATED = "UserCreated";

    // Incoming: scheduling's outbox exchange (rabbitmq-outbox-migration Phase 8,
    // previously Kafka topic outbox.event.ExamSession, shared with ScoringRequested/
    // PublishRequested/SessionScheduled). Not ordering-sensitive.
    public static final String SCHEDULING_OUTBOX_EXCHANGE = "outbox.scheduling.exchange";
    public static final String QUEUE_SESSION_EVENTS = "notification.session-events";
    public static final String QUEUE_SESSION_EVENTS_DLQ = "notification.session-events.dlq";
    public static final String SESSION_EVENTS_ROUTING_PATTERN = "ExamSession.*";
    public static final String SESSION_EVENTS_DEAD_LETTER_ROUTING_KEY = "session-events";
    public static final String INCOMING_EVENT_STUDENT_ENROLLED = "StudentEnrolled";

    // Incoming: reporting's outbox exchange (rabbitmq-outbox-migration Phase 8,
    // previously Kafka topic outbox.event.AttemptReport). Not ordering-sensitive.
    public static final String REPORTING_OUTBOX_EXCHANGE = "outbox.reporting.exchange";
    public static final String QUEUE_ATTEMPT_REPORT_EVENTS = "notification.attempt-report-events";
    public static final String QUEUE_ATTEMPT_REPORT_EVENTS_DLQ = "notification.attempt-report-events.dlq";
    public static final String ATTEMPT_REPORT_EVENTS_ROUTING_PATTERN = "AttemptReport.*";
    public static final String ATTEMPT_REPORT_EVENTS_DEAD_LETTER_ROUTING_KEY = "attempt-report-events";
    public static final String INCOMING_EVENT_ATTEMPT_PUBLISHED = "AttemptPublished";

    // Incoming: proctor's outbox exchange (rabbitmq-outbox-migration Phase 8,
    // previously Kafka topic outbox.event.ViolationEvent). Not ordering-sensitive.
    public static final String PROCTOR_OUTBOX_EXCHANGE = "outbox.proctor.exchange";
    public static final String QUEUE_VIOLATION_EVENTS = "notification.violation-events";
    public static final String QUEUE_VIOLATION_EVENTS_DLQ = "notification.violation-events.dlq";
    public static final String VIOLATION_EVENTS_ROUTING_PATTERN = "ViolationEvent.*";
    public static final String VIOLATION_EVENTS_DEAD_LETTER_ROUTING_KEY = "violation-events";
    public static final String INCOMING_EVENT_VIOLATION_DETECTED = "ViolationDetected";

    public static final String ROLE_HOST_ADMIN = "HOST_ADMIN";

    // RabbitMQ (mirrors scoring's Phase 9 AI-scoring queue shape).
    public static final String EMAIL_EXCHANGE = "notification.email";
    public static final String EMAIL_QUEUE = "notification.email-jobs";
    public static final String EMAIL_DLQ = "notification.email-jobs.dlq";
    public static final String EMAIL_ROUTING_KEY = "email-job";

    private NotificationConstants() {
    }
}
