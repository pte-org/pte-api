package com.pte.notification.internal.constant;

/** Stable codes are distinct from approved display copy and existing email constants. */
public final class InboxConstants {
    public static final String INVALID_REQUEST = "INBOX_INVALID_REQUEST";
    public static final String INVALID_REQUEST_MESSAGE = "The notification request is invalid.";
    public static final String EVENT_CONFLICT = "INBOX_EVENT_CONFLICT";
    public static final String EVENT_CONFLICT_MESSAGE = "This notification event has already been recorded with different content.";
    public static final String RECIPIENT_SCOPE_CONFLICT = "INBOX_RECIPIENT_SCOPE_CONFLICT";
    public static final String RECIPIENT_SCOPE_CONFLICT_MESSAGE = "The notification recipient scope has changed.";
    public static final String TRANSIENT_FAILURE = "INBOX_DELIVERY_TRANSIENT_FAILURE";
    public static final String NON_RETRYABLE_FAILURE = "INBOX_DELIVERY_NON_RETRYABLE_FAILURE";
    public static final String LEASE_EXHAUSTED = "INBOX_DELIVERY_LEASE_EXHAUSTED";
    public static final String CLAIM_LOST = "INBOX_DELIVERY_CLAIM_LOST";
    public static final String CLAIM_LOST_MESSAGE = "This notification delivery is no longer owned by the worker.";
    public static final int SCHEMA_VERSION = 1;
    public static final int EVENT_KEY_LIMIT = 255;
    public static final int TITLE_LIMIT = 150;
    public static final int BODY_LIMIT = 5000;
    public static final int RECOVERY_BATCH_LIMIT = 200;
    private InboxConstants() { }
}
