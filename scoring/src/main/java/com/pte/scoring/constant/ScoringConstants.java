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

    // Objective task types this phase can grade (Milestone 1: MCQ only — speaking/writing need Phase 9's AI vendor)
    public static final String TASK_TYPE_MC_READING_SINGLE = "MC_READING_SINGLE";

    private ScoringConstants() {
    }
}
