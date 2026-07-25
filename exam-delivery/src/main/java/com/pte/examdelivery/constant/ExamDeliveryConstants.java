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

    public static final String CACHE_KEY_PREFIX = "exam-delivery:pinned-snapshot:";
    public static final String LOCK_KEY_PREFIX = "exam-delivery:lock:pinned-snapshot:";

    private ExamDeliveryConstants() {
    }
}
