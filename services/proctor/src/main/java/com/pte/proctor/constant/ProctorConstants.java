package com.pte.proctor.constant;

/** Centralized codes/labels for proctor. */
public final class ProctorConstants {

    public static final String PROCTOR_SESSION_NOT_FOUND = "PROCTOR_SESSION_NOT_FOUND";
    public static final String PROCTOR_SESSION_NOT_ACTIVE = "PROCTOR_SESSION_NOT_ACTIVE";
    public static final String PROCTOR_ASSIGNMENT_CHECK_FAILED = "PROCTOR_ASSIGNMENT_CHECK_FAILED";
    public static final String NOT_ASSIGNED_TO_SESSION = "NOT_ASSIGNED_TO_SESSION";
    public static final String EXTRA_SECONDS_REQUIRED = "EXTRA_SECONDS_REQUIRED";
    public static final String PROCTOR_ROLE_REQUIRED = "PROCTOR_ROLE_REQUIRED";

    // Outgoing (proctor's own outbox)
    public static final String AGGREGATE_PROCTOR_COMMAND = "ProctorCommand";
    public static final String EVENT_PROCTOR_COMMAND = "ProctorCommand";
    public static final String AGGREGATE_VIOLATION_EVENT = "ViolationEvent";
    public static final String EVENT_VIOLATION_DETECTED = "ViolationDetected";

    // RabbitMQ outbox relay (rabbitmq-outbox-migration Phase 2). Downstream
    // consumers (e.g. exam-delivery's ProctorCommandConsumer, Phase 5) bind
    // their own queue to this exchange with routing key "{aggregateType}.{eventType}".
    public static final String OUTBOX_EXCHANGE = "outbox.proctor.exchange";

    // STOMP
    public static final String TOPIC_PREFIX = "/topic/proctor-sessions/";
    public static final String STOMP_SUBSCRIPTION_FORBIDDEN = "STOMP_SUBSCRIPTION_FORBIDDEN";
    public static final String STOMP_COMMAND_FORBIDDEN = "STOMP_COMMAND_FORBIDDEN";

    private ProctorConstants() {
    }
}
