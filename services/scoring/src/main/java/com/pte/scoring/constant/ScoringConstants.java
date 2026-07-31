package com.pte.scoring.constant;

/** Centralized codes/labels for scoring. */
public final class ScoringConstants {

    // Incoming: exam-delivery's outbox exchange (rabbitmq-outbox-migration
    // Phase 6, previously Kafka topic outbox.event.ExamAttempt). Ordering-sensitive:
    // routed through a SINGLE durable queue with listener concurrency pinned to
    // 1, so RabbitMQ's per-queue FIFO preserves per-attempt answer-ingestion
    // order (plan.md, CONFIRMED — depends on exam-delivery's own relay also
    // staying single-instance, see ExamDeliveryOutboxRelay in Phase 5).
    public static final String EXAMDELIVERY_OUTBOX_EXCHANGE = "outbox.examdelivery.exchange";
    public static final String QUEUE_ANSWER_INGEST = "scoring.answer-ingest";
    public static final String QUEUE_ANSWER_INGEST_DLQ = "scoring.answer-ingest.dlq";
    public static final String ANSWER_INGEST_ROUTING_PATTERN = "ExamAttempt.*";
    public static final String ANSWER_INGEST_DEAD_LETTER_ROUTING_KEY = "answer-ingest";
    public static final String INCOMING_EVENT_ANSWER_SUBMITTED = "AnswerSubmitted";

    // Incoming: scheduling's outbox exchange (rabbitmq-outbox-migration Phase 6,
    // previously Kafka topic outbox.event.ExamSession). Not ordering-sensitive —
    // host commands, one-shot, normal listener concurrency.
    public static final String SCHEDULING_OUTBOX_EXCHANGE = "outbox.scheduling.exchange";
    public static final String QUEUE_SCORING_COMMAND = "scoring.scoring-command";
    public static final String QUEUE_SCORING_COMMAND_DLQ = "scoring.scoring-command.dlq";
    public static final String SCORING_COMMAND_ROUTING_PATTERN = "ExamSession.*";
    public static final String SCORING_COMMAND_DEAD_LETTER_ROUTING_KEY = "scoring-command";
    public static final String INCOMING_EVENT_SCORING_REQUESTED = "ScoringRequested";

    // AMQP replacement for the old Kafka record header of the same purpose —
    // set by AbstractOutboxRelay#publishAndConfirm when publishing, read via
    // Message.getMessageProperties().getHeaders() here.
    public static final String EVENT_TYPE_HEADER = "eventType";

    // Outgoing (scoring's own outbox). RabbitMQ outbox relay (rabbitmq-outbox-migration
    // Phase 6): downstream consumers (e.g. reporting's AnswerScoredConsumer, Phase 7)
    // bind their own queue to this exchange with routing key "{aggregateType}.{eventType}".
    public static final String OUTBOX_EXCHANGE = "outbox.scoring.exchange";
    public static final String AGGREGATE_ANSWER = "ScoringAnswer";
    public static final String AGGREGATE_ATTEMPT = "ScoringAttempt";
    public static final String EVENT_ANSWER_SCORED = "AnswerScored";
    public static final String EVENT_ATTEMPT_SCORED = "AttemptScored";

    // Objective task types (Phase 7): rule-based, synchronous, no vendor call.
    public static final String TASK_TYPE_MC_READING_SINGLE = "MC_READING_SINGLE";

    // AI-scorable task types (Phase 9): routed to the RabbitMQ vendor work queue.
    public static final String TASK_TYPE_READ_ALOUD = "READ_ALOUD";
    public static final String TASK_TYPE_WRITE_ESSAY = "WRITE_ESSAY";

    // RabbitMQ (Phase 9 — activates what Phase 7 deferred: slow/unreliable vendor calls need a queue, objective scoring didn't).
    public static final String AI_SCORING_EXCHANGE = "scoring.ai-scoring";
    public static final String AI_SCORING_QUEUE = "scoring.ai-scoring-jobs";
    public static final String AI_SCORING_DLQ = "scoring.ai-scoring-jobs.dlq";
    public static final String AI_SCORING_ROUTING_KEY = "ai-scoring-job";

    public static final String REVIEW_NOT_PENDING = "REVIEW_NOT_PENDING";

    private ScoringConstants() {
    }
}
