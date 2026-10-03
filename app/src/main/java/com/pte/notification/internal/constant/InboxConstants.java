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
    public static final String ROLE_REQUIRED = "INBOX_ROLE_REQUIRED";
    public static final String ROLE_REQUIRED_MESSAGE = "An admin or host role is required to access the inbox.";
    public static final String PAGE_INVALID = "INBOX_PAGE_INVALID";
    public static final String PAGE_INVALID_MESSAGE = "Page must be zero or greater.";
    public static final String SIZE_INVALID = "INBOX_SIZE_INVALID";
    public static final String SIZE_INVALID_MESSAGE = "Page size must be between 1 and 100.";
    public static final String FILTER_INVALID = "INBOX_FILTER_INVALID";
    public static final String FILTER_INVALID_MESSAGE = "The notification filter is invalid.";
    public static final String CATEGORY_INVALID = "INBOX_CATEGORY_INVALID";
    public static final String CATEGORY_INVALID_MESSAGE = "The notification category is invalid.";
    public static final String SNAPSHOT_INVALID = "INBOX_SNAPSHOT_INVALID";
    public static final String SNAPSHOT_INVALID_MESSAGE = "The notification snapshot is invalid or expired.";
    public static final String ITEM_NOT_FOUND = "INBOX_ITEM_NOT_FOUND";
    public static final String ITEM_NOT_FOUND_MESSAGE = "The notification was not found.";
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int RECENT_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 100;
    public static final long SNAPSHOT_TTL_SECONDS = 900;
    public static final int SNAPSHOT_CLEANUP_BATCH_LIMIT = 200;
    public static final int SCHEMA_VERSION = 1;
    public static final int EVENT_KEY_LIMIT = 255;
    public static final int TITLE_LIMIT = 150;
    public static final int BODY_LIMIT = 5000;
    public static final int RECOVERY_BATCH_LIMIT = 200;
    private InboxConstants() { }
}
