package com.pte.scoring.constant;

/** Centralized codes/labels for scoring. */
public final class ScoringConstants {

    // Incoming: exam-delivery's outbox.event.ExamAttempt topic
    public static final String TOPIC_ATTEMPT_EVENTS = "outbox.event.ExamAttempt";
    public static final String INCOMING_EVENT_ANSWER_SUBMITTED = "AnswerSubmitted";

    // Incoming: scheduling's outbox.event.ExamSession topic
    public static final String TOPIC_SESSION_EVENTS = "outbox.event.ExamSession";
    public static final String INCOMING_EVENT_SCORING_REQUESTED = "ScoringRequested";

    public static final String KAFKA_HEADER_EVENT_TYPE = "eventType";

    // Outgoing (scoring's own outbox)
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
