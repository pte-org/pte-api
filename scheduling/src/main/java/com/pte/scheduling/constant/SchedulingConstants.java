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
    public static final String HOST_CONTEXT_REQUIRED = "HOST_CONTEXT_REQUIRED";
    public static final String INVALID_SESSION_WINDOW = "INVALID_SESSION_WINDOW";
    public static final String NOT_ENTITLED = "NOT_ENTITLED";

    public static final String AGGREGATE_SESSION = "ExamSession";
    public static final String EVENT_SESSION_SCHEDULED = "SessionScheduled";
    public static final String EVENT_STUDENT_ENROLLED = "StudentEnrolled";
    public static final String EVENT_SCORING_REQUESTED = "ScoringRequested";
    public static final String EVENT_PUBLISH_REQUESTED = "PublishRequested";

    private SchedulingConstants() {
    }
}
