package com.aptis.modules.examdelivery.constant;

public final class ExamDeliveryConstants {

    public static final String OP_ATTEMPT_SUBMIT = "examdelivery.attempt.submit";
    public static final String OP_ATTEMPT_SECTIONS = "examdelivery.attempt.sections";
    public static final String OP_ATTEMPT_SECTION_QUESTIONS = "examdelivery.attempt.section-questions";

    // Error Messages
    public static final String ATTEMPT_NOT_FOUND = "Exam attempt not found with ID: ";
    public static final String ATTEMPT_ALREADY_SUBMITTED = "Cannot submit answers: this exam attempt has already been submitted";
    public static final String ATTEMPT_ALREADY_SUBMITTED_DOMAIN = "This exam attempt has already been submitted and cannot be resubmitted";
    public static final String SESSION_NOT_YET_AVAILABLE = "This exam session is not yet available";
    public static final String SESSION_NO_LONGER_AVAILABLE = "This exam session is no longer available";
    public static final String SKILL_NOT_IN_SESSION_SUBSET = "This skill is not part of the configured session";
    public static final String INVALID_STATUS_TRANSITION = "This action is not valid for the attempt's current status";

    // Heartbeat timeout: elapsed-time threshold, not a missed-beat counter (Phase 6 Design Constraint).
    public static final int HEARTBEAT_TIMEOUT_SECONDS = 35;
    public static final long HEARTBEAT_SCAN_INTERVAL_MS = 10_000L;

    public static final String OP_ATTEMPT_HEARTBEAT = "examdelivery.attempt.heartbeat";
    public static final String OP_ATTEMPT_SECTION_SWITCH = "examdelivery.attempt.section-switch";
    public static final String OP_ATTEMPT_FINAL_SUBMIT = "examdelivery.attempt.final-submit";

    // Response keys and values
    public static final String RESP_STATUS_KEY = "status";
    public static final String RESP_STATUS_SUCCESS = "SUCCESS";
    public static final String RESP_MESSAGE_KEY = "message";
    public static final String RESP_MESSAGE_SUCCESS = "Answer recorded successfully.";

    // Storage folders
    public static final String FOLDER_ATTEMPTS = "attempts/";

    // Grader error codes
    public static final String GRADER_ATTEMPT_NOT_FOUND = "ATTEMPT_NOT_FOUND";
    public static final String GRADER_ANSWER_NOT_FOUND = "ANSWER_NOT_FOUND";
    public static final String GRADER_ORG_NOT_ASSIGNED = "GRADER_ORG_NOT_ASSIGNED";
    public static final String GRADER_ANSWER_CONCURRENT_MODIFICATION = "ANSWER_CONCURRENT_MODIFICATION";
    public static final String GRADER_INVALID_MANUAL_SCORE = "INVALID_MANUAL_SCORE";

    // Retry request
    public static final String OP_RETRY_REQUEST = "examdelivery.retry.request";
    public static final String OP_RETRY_LIST_PENDING = "examdelivery.retry.list-pending";
    public static final String OP_RETRY_APPROVE = "examdelivery.retry.approve";
    public static final String OP_RETRY_REJECT = "examdelivery.retry.reject";
    public static final String RETRY_REQUEST_NOT_FOUND = "Retry request not found with ID: ";
    public static final String RETRY_ATTEMPT_NOT_SUBMITTED = "Cannot request a retry: this attempt has not been submitted yet";
    public static final String RETRY_ALREADY_PENDING_OR_APPROVED = "A retry request for this exam is already pending or approved";
    public static final String RETRY_BUDGET_EXHAUSTED = "Retry budget exhausted for this exam";
    public static final String RETRY_PENDING_BLOCKS_START = "A pending retry request must be reviewed before this exam can be started again";

    private ExamDeliveryConstants() {
    }
}
