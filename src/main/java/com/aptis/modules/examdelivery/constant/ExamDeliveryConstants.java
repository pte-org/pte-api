package com.aptis.modules.examdelivery.constant;

public final class ExamDeliveryConstants {

    public static final String OP_ATTEMPT_SUBMIT = "examdelivery.attempt.submit";

    // Error Messages
    public static final String ATTEMPT_NOT_FOUND = "Exam attempt not found with ID: ";
    public static final String ATTEMPT_ALREADY_SUBMITTED = "Cannot submit answers: this exam attempt has already been submitted";
    public static final String ATTEMPT_ALREADY_SUBMITTED_DOMAIN = "This exam attempt has already been submitted and cannot be resubmitted";

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

    private ExamDeliveryConstants() {
    }
}
