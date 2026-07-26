package com.pte.reporting.constant;

/** Centralized codes/labels for reporting. */
public final class ReportingConstants {

    public static final String REPORT_NOT_FOUND = "REPORT_NOT_FOUND";

    // Incoming: exam-delivery's outbox.event.ExamAttempt topic
    public static final String TOPIC_ATTEMPT_EVENTS = "outbox.event.ExamAttempt";
    public static final String INCOMING_EVENT_ATTEMPT_SUBMITTED = "AttemptSubmitted";
    public static final String INCOMING_EVENT_ANSWER_SUBMITTED = "AnswerSubmitted";

    // Incoming: scoring's outbox.event.ScoringAnswer topic
    public static final String TOPIC_SCORING_ANSWER_EVENTS = "outbox.event.ScoringAnswer";
    public static final String INCOMING_EVENT_ANSWER_SCORED = "AnswerScored";

    // Incoming: scheduling's outbox.event.ExamSession topic
    public static final String TOPIC_SESSION_EVENTS = "outbox.event.ExamSession";
    public static final String INCOMING_EVENT_PUBLISH_REQUESTED = "PublishRequested";

    public static final String KAFKA_HEADER_EVENT_TYPE = "eventType";

    // Outgoing (reporting's own outbox)
    public static final String AGGREGATE_ATTEMPT_REPORT = "AttemptReport";
    public static final String EVENT_ATTEMPT_PUBLISHED = "AttemptPublished";

    private ReportingConstants() {
    }
}
