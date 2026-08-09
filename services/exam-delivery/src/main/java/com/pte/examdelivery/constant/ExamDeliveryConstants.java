package com.pte.examdelivery.constant;

public final class ExamDeliveryConstants {

    public static final String ATTEMPT_NOT_FOUND = "ATTEMPT_NOT_FOUND";
    public static final String ALREADY_ATTEMPTED = "ALREADY_ATTEMPTED";
    public static final String ATTEMPT_NOT_IN_PROGRESS = "ATTEMPT_NOT_IN_PROGRESS";
    public static final String NOT_ENTITLED = "NOT_ENTITLED";
    public static final String ENTITLEMENT_CHECK_FAILED = "ENTITLEMENT_CHECK_FAILED";
    public static final String SNAPSHOT_CONTENT_FETCH_FAILED = "SNAPSHOT_CONTENT_FETCH_FAILED";
    public static final String TASK_TIMING_NOT_CONFIGURED = "TASK_TIMING_NOT_CONFIGURED";
    public static final String RESPONSE_WINDOW_EXPIRED = "RESPONSE_WINDOW_EXPIRED";
    public static final String NOT_CURRENT_TASK = "NOT_CURRENT_TASK";
    public static final String ATTEMPT_ALREADY_COMPLETE = "ATTEMPT_ALREADY_COMPLETE";

    public static final String AGGREGATE_ATTEMPT = "ExamAttempt";
    public static final String EVENT_ANSWER_SUBMITTED = "AnswerSubmitted";
    public static final String EVENT_ATTEMPT_SUBMITTED = "AttemptSubmitted";

    // RabbitMQ outbox relay (rabbitmq-outbox-migration Phase 5). Downstream
    // consumers (e.g. scoring's AnswerIngestConsumer, Phase 6) bind their own
    // queue to this exchange with routing key "{aggregateType}.{eventType}".
    // IMPORTANT: this service's relay must stay single-instance — scoring's
    // AnswerIngestConsumer (Phase 6) depends on AnswerSubmitted events
    // arriving in the order this outbox wrote them (plan.md, CONFIRMED).
    public static final String OUTBOX_EXCHANGE = "outbox.examdelivery.exchange";

    // Incoming: proctor's outbox exchange (rabbitmq-outbox-migration Phase 5,
    // previously Kafka topic outbox.event.ProctorCommand). Ordering-sensitive:
    // routed through a SINGLE durable queue with listener concurrency pinned
    // to 1, so RabbitMQ's per-queue FIFO preserves per-attempt command order
    // (plan.md, CONFIRMED — depends on proctor's own relay also staying
    // single-instance, see ProctorOutboxRelay in Phase 2).
    public static final String PROCTOR_OUTBOX_EXCHANGE = "outbox.proctor.exchange";
    public static final String QUEUE_PROCTOR_COMMANDS = "examdelivery.proctor-commands";
    public static final String QUEUE_PROCTOR_COMMANDS_DLQ = "examdelivery.proctor-commands.dlq";
    public static final String PROCTOR_COMMANDS_ROUTING_PATTERN = "ProctorCommand.*";
    public static final String PROCTOR_COMMANDS_DEAD_LETTER_ROUTING_KEY = "proctor-commands";
    public static final String COMMAND_TYPE_FORCE_SUBMIT = "FORCE_SUBMIT";
    public static final String COMMAND_TYPE_EXTEND_TIME = "EXTEND_TIME";

    public static final String CACHE_KEY_PREFIX = "exam-delivery:pinned-snapshot:";
    public static final String LOCK_KEY_PREFIX = "exam-delivery:lock:pinned-snapshot:";

    private ExamDeliveryConstants() {
    }
}
