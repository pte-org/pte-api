package com.pte.billing.internal.constant;

/** Error codes and validation messages owned by the billing module. */
public final class BillingConstants {

    public static final String TENANT_APPLICATION_NOT_FOUND = "TENANT_APPLICATION_NOT_FOUND";
    public static final String REQUESTED_CODE_ALREADY_USED = "REQUESTED_CODE_ALREADY_USED";
    public static final String APPLICATION_NOT_PENDING = "APPLICATION_NOT_PENDING";

    public static final String ORG_NAME_REQUIRED = "Organization name is required";
    public static final String ORG_TYPE_REQUIRED = "Organization type is required";
    public static final String REQUESTED_CODE_REQUIRED = "Requested code is required";
    public static final String REQUESTED_CODE_INVALID =
            "Requested code must be 3-32 lowercase letters, digits, or hyphens";
    public static final String CONTACT_EMAIL_REQUIRED = "Contact email is required";
    public static final String CONTACT_EMAIL_INVALID = "Contact email must be valid";
    public static final String REJECT_REASON_REQUIRED = "Reject reason is required";

    /**
     * Hardcoded until Phase 3 adds {@code PlatformSetting} — a real
     * platform-configurable value, not a permanent constant.
     */
    public static final int TEMPORARY_FREE_STUDENT_LIMIT = 50;

    private BillingConstants() {
    }
}
