package com.pte.reporting.constant;

/** Centralized codes/labels for reporting. */
public final class ReportingConstants {

    public static final String REPORT_NOT_FOUND = "REPORT_NOT_FOUND";

    // Incoming: exam-delivery's outbox exchange (rabbitmq-outbox-migration Phase 7,
    // previously Kafka topic outbox.event.ExamAttempt). Not ordering-sensitive
    // (upserts idempotently by attemptPublicId/answerPublicId) — normal concurrency.
    public static final String EXAMDELIVERY_OUTBOX_EXCHANGE = "outbox.examdelivery.exchange";
    public static final String QUEUE_ATTEMPT_INGEST = "reporting.attempt-ingest";
    public static final String QUEUE_ATTEMPT_INGEST_DLQ = "reporting.attempt-ingest.dlq";
    public static final String ATTEMPT_INGEST_ROUTING_PATTERN = "ExamAttempt.*";
    public static final String ATTEMPT_INGEST_DEAD_LETTER_ROUTING_KEY = "attempt-ingest";
    public static final String INCOMING_EVENT_ATTEMPT_SUBMITTED = "AttemptSubmitted";
    public static final String INCOMING_EVENT_ANSWER_SUBMITTED = "AnswerSubmitted";

    // Incoming: scoring's outbox exchange (rabbitmq-outbox-migration Phase 7,
    // previously Kafka topic outbox.event.ScoringAnswer). Not ordering-sensitive
    // (updates an existing projection row by id) — normal concurrency.
    public static final String SCORING_OUTBOX_EXCHANGE = "outbox.scoring.exchange";
    public static final String QUEUE_ANSWER_SCORED = "reporting.answer-scored";
    public static final String QUEUE_ANSWER_SCORED_DLQ = "reporting.answer-scored.dlq";
    public static final String ANSWER_SCORED_ROUTING_PATTERN = "ScoringAnswer.*";
    public static final String ANSWER_SCORED_DEAD_LETTER_ROUTING_KEY = "answer-scored";
    public static final String INCOMING_EVENT_ANSWER_SCORED = "AnswerScored";

    // Incoming: scheduling's outbox exchange (rabbitmq-outbox-migration Phase 7,
    // previously Kafka topic outbox.event.ExamSession). Not ordering-sensitive
    // (acts on a session-level command) — normal concurrency.
    public static final String SCHEDULING_OUTBOX_EXCHANGE = "outbox.scheduling.exchange";
    public static final String QUEUE_PUBLISH = "reporting.publish";
    public static final String QUEUE_PUBLISH_DLQ = "reporting.publish.dlq";
    public static final String PUBLISH_ROUTING_PATTERN = "ExamSession.*";
    public static final String PUBLISH_DEAD_LETTER_ROUTING_KEY = "publish";
    public static final String INCOMING_EVENT_PUBLISH_REQUESTED = "PublishRequested";

    // AMQP replacement for the old Kafka record header of the same purpose —
    // set by AbstractOutboxRelay#publishAndConfirm when publishing, read via
    // Message.getMessageProperties().getHeaders() here.
    public static final String EVENT_TYPE_HEADER = "eventType";

    // Outgoing (reporting's own outbox). RabbitMQ outbox relay (rabbitmq-outbox-migration
    // Phase 7): downstream consumers bind their own queue to this exchange with
    // routing key "{aggregateType}.{eventType}".
    public static final String OUTBOX_EXCHANGE = "outbox.reporting.exchange";
    public static final String AGGREGATE_ATTEMPT_REPORT = "AttemptReport";
    public static final String EVENT_ATTEMPT_PUBLISHED = "AttemptPublished";

    private ReportingConstants() {
    }
}
