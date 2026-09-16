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

    private NotificationConstants() {
    }
}
