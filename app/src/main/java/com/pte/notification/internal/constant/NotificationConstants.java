package com.pte.notification.internal.constant;

/** Centralized codes/labels for notification. */
public final class NotificationConstants {

    public static final String ROLE_HOST_ADMIN = "HOST_ADMIN";

    // RabbitMQ (email send only — genuine external SMTP work, unlike the
    // application-event triggers above which are in-process now).
    public static final String EMAIL_EXCHANGE = "notification.email";
    public static final String EMAIL_QUEUE = "notification.email-jobs";
    public static final String EMAIL_DLQ = "notification.email-jobs.dlq";
    public static final String EMAIL_ROUTING_KEY = "email-job";

    public static final String EMAIL_SEND_RETRIES_EXHAUSTED = "Email send retries exhausted";
    public static final String EMAIL_SEND_FAILED = "Email send failed for %s";
    public static final String SENSITIVE_EMAIL_BODY_REDACTED =
            "Sensitive email content is not retained in notification history.";
    public static final String SESSION_CANCELLED_SUBJECT = "Exam session cancelled";
    public static final String SESSION_CANCELLED_BODY =
            "The exam session \"%s\" was cancelled at %s. Please review the exam workspace.";
    public static final String SESSION_CANCELLED_DEDUPE_KEY = "exam-session-cancelled:%s:%s";
    public static final String STUDENT_ENROLLMENT_CONFLICT_SUBJECT = "Students skipped because of exam time conflicts";
    public static final String STUDENT_ENROLLMENT_CONFLICT_BODY =
            "The exam session \"%s\" skipped these students because they are enrolled in another overlapping exam:\n%s";
    public static final String STUDENT_ENROLLMENT_CONFLICT_DEDUPE_KEY =
            "student-enrollment-conflict:%s:%s:%s";

    public static final String ATTEMPT_RESULT_SUBJECT = "Your exam results";
    public static final String ATTEMPT_RESULT_GREETING_NAMED = "Hello %s,";
    public static final String ATTEMPT_RESULT_GREETING_ANONYMOUS = "Hello,";
    public static final String ATTEMPT_RESULT_INTRO = "Your exam results have been published.";
    public static final String ATTEMPT_RESULT_LINE = "%s: %s";
    public static final String ATTEMPT_RESULT_OVERALL_LABEL = "Overall score";
    public static final String ATTEMPT_RESULT_LISTENING_LABEL = "Listening";
    public static final String ATTEMPT_RESULT_READING_LABEL = "Reading";
    public static final String ATTEMPT_RESULT_SPEAKING_LABEL = "Speaking";
    public static final String ATTEMPT_RESULT_WRITING_LABEL = "Writing";
    public static final String ATTEMPT_RESULT_INSUFFICIENT_DATA = "Not enough data";
    public static final String ATTEMPT_RESULT_FOOTER = "Please keep this email for your records.";
    public static final String ATTEMPT_RESULT_DEDUPE_KEY = "attempt-published:%s";
    public static final String ATTEMPT_RESULT_NO_RECIPIENT_EMAIL =
            "Skipping results email for attempt {} (tenantId={}): the student has no email address";

    private NotificationConstants() {
    }
}
