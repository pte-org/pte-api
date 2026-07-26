package com.pte.notification.constant;

/** Centralized codes/labels for notification. */
public final class NotificationConstants {

    public static final String KAFKA_HEADER_EVENT_TYPE = "eventType";

    // Incoming: iam's outbox.event.User topic.
    public static final String TOPIC_USER_EVENTS = "outbox.event.User";
    public static final String INCOMING_EVENT_USER_CREATED = "UserCreated";

    // Incoming: scheduling's outbox.event.ExamSession topic (shared with ScoringRequested/PublishRequested/SessionScheduled).
    public static final String TOPIC_SESSION_EVENTS = "outbox.event.ExamSession";
    public static final String INCOMING_EVENT_STUDENT_ENROLLED = "StudentEnrolled";

    // Incoming: reporting's outbox.event.AttemptReport topic.
    public static final String TOPIC_ATTEMPT_REPORT_EVENTS = "outbox.event.AttemptReport";
    public static final String INCOMING_EVENT_ATTEMPT_PUBLISHED = "AttemptPublished";

    // Incoming: proctor's outbox.event.ViolationEvent topic.
    public static final String TOPIC_VIOLATION_EVENTS = "outbox.event.ViolationEvent";
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
