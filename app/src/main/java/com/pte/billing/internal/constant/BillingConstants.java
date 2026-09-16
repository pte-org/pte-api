package com.pte.billing.internal.constant;

/** Error codes and validation messages owned by the billing module. */
public final class BillingConstants {

    public static final String TENANT_APPLICATION_NOT_FOUND = "TENANT_APPLICATION_NOT_FOUND";
    public static final String REQUESTED_CODE_ALREADY_USED = "REQUESTED_CODE_ALREADY_USED";
    public static final String APPLICATION_NOT_PENDING = "APPLICATION_NOT_PENDING";
    public static final String PLAN_NOT_FOUND = "PLAN_NOT_FOUND";
    public static final String PLAN_TYPE_INVALID = "PLAN_TYPE_INVALID";
    public static final String PLAN_MUST_BE_DRAFT_TO_ACTIVATE = "PLAN_MUST_BE_DRAFT_TO_ACTIVATE";
    public static final String PLAN_ALREADY_ARCHIVED = "PLAN_ALREADY_ARCHIVED";
    public static final String PLAN_ARCHIVED_NOT_EDITABLE = "PLAN_ARCHIVED_NOT_EDITABLE";
    public static final String PLATFORM_SETTING_NOT_FOUND = "PLATFORM_SETTING_NOT_FOUND";
    public static final String PLATFORM_SETTING_INVALID = "PLATFORM_SETTING_INVALID";
    public static final String SUBSCRIPTION_TENANT_REQUIRED = "SUBSCRIPTION_TENANT_REQUIRED";
    public static final String SUBSCRIPTION_PLAN_REQUIRED = "SUBSCRIPTION_PLAN_REQUIRED";
    public static final String SUBSCRIPTION_SOURCE_REQUIRED = "SUBSCRIPTION_SOURCE_REQUIRED";
    public static final String SUBSCRIPTION_PLAN_TYPE_INVALID = "SUBSCRIPTION_PLAN_TYPE_INVALID";
    public static final String SUBSCRIPTION_PLAN_NOT_ACTIVE = "SUBSCRIPTION_PLAN_NOT_ACTIVE";
    public static final String SUBSCRIPTION_EXAM_FIELDS_INVALID = "SUBSCRIPTION_EXAM_FIELDS_INVALID";
    public static final String SUBSCRIPTION_CAPACITY_FIELDS_INVALID = "SUBSCRIPTION_CAPACITY_FIELDS_INVALID";
    public static final String LICENSE_KEY_GENERATION_FAILED = "LICENSE_KEY_GENERATION_FAILED";
    public static final String ORDER_TENANT_REQUIRED = "ORDER_TENANT_REQUIRED";
    public static final String ORDER_PLAN_REQUIRED = "ORDER_PLAN_REQUIRED";
    public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
    public static final String ORDER_PLAN_NOT_ACTIVE = "ORDER_PLAN_NOT_ACTIVE";
    public static final String ORDER_PENDING_EXISTS = "ORDER_PENDING_EXISTS";
    public static final String ORDER_AMOUNT_INVALID = "ORDER_AMOUNT_INVALID";
    public static final String ORDER_CURRENCY_UNSUPPORTED = "ORDER_CURRENCY_UNSUPPORTED";
    public static final String PAYOS_CLIENT_ID_REQUIRED = "PAYOS_CLIENT_ID_REQUIRED";
    public static final String PAYOS_API_KEY_REQUIRED = "PAYOS_API_KEY_REQUIRED";
    public static final String PAYOS_CHECKSUM_KEY_REQUIRED = "PAYOS_CHECKSUM_KEY_REQUIRED";
    public static final String PAYOS_BASE_URL_REQUIRED = "PAYOS_BASE_URL_REQUIRED";
    public static final String PAYOS_RETURN_URL_REQUIRED = "PAYOS_RETURN_URL_REQUIRED";
    public static final String PAYOS_CANCEL_URL_REQUIRED = "PAYOS_CANCEL_URL_REQUIRED";
    public static final String PAYOS_TIMEOUT_INVALID = "PAYOS_TIMEOUT_INVALID";
    public static final String PAYOS_REQUEST_FAILED = "PAYOS_REQUEST_FAILED";
    public static final String PAYOS_RESPONSE_INVALID = "PAYOS_RESPONSE_INVALID";
    public static final String PAYOS_SIGNATURE_INVALID = "PAYOS_SIGNATURE_INVALID";
    public static final String PAYOS_WEBHOOK_INVALID = "PAYOS_WEBHOOK_INVALID";
    public static final String PAYOS_ORDER_NOT_FOUND = "PAYOS_ORDER_NOT_FOUND";
    public static final String PAYOS_ORDER_AMOUNT_MISMATCH = "PAYOS_ORDER_AMOUNT_MISMATCH";
    public static final String PAYOS_ORDER_CURRENCY_MISMATCH = "PAYOS_ORDER_CURRENCY_MISMATCH";
    public static final String PAYOS_CANCELLATION_FAILED = "PAYOS_CANCELLATION_FAILED";
    public static final String LICENSE_CODE_REQUIRED = "LICENSE_CODE_REQUIRED";
    public static final String LICENSE_CODE_NOT_FOUND = "LICENSE_CODE_NOT_FOUND";
    public static final String LICENSE_CODE_PLAN_REQUIRED = "LICENSE_CODE_PLAN_REQUIRED";
    public static final String LICENSE_CODE_PLAN_NOT_ACTIVE = "LICENSE_CODE_PLAN_NOT_ACTIVE";
    public static final String LICENSE_CODE_EXPIRY_INVALID = "LICENSE_CODE_EXPIRY_INVALID";
    public static final String LICENSE_CODE_REVOKE_REASON_REQUIRED = "LICENSE_CODE_REVOKE_REASON_REQUIRED";
    public static final String LICENSE_CODE_REVOKE_REASON_MAX = "LICENSE_CODE_REVOKE_REASON_MAX";
    public static final String LICENSE_CODE_REVOKED = "LICENSE_CODE_REVOKED";
    public static final String LICENSE_CODE_ALREADY_REDEEMED = "LICENSE_CODE_ALREADY_REDEEMED";
    public static final String LICENSE_CODE_EXPIRED = "LICENSE_CODE_EXPIRED";
    public static final String LICENSE_CODE_NOT_REDEEMABLE = "LICENSE_CODE_NOT_REDEEMABLE";
    public static final String LICENSE_CODE_ALREADY_REVOKED = "LICENSE_CODE_ALREADY_REVOKED";
    public static final String LICENSE_CODE_NOT_REVOCABLE = "LICENSE_CODE_NOT_REVOCABLE";
    public static final String LICENSE_CODE_TENANT_REQUIRED = "LICENSE_CODE_TENANT_REQUIRED";
    public static final String LICENSE_CODE_PLATFORM_ADMIN_REQUIRED = "LICENSE_CODE_PLATFORM_ADMIN_REQUIRED";
    public static final String LICENSE_CODE_HOST_ADMIN_REQUIRED = "LICENSE_CODE_HOST_ADMIN_REQUIRED";
    public static final String LICENSE_CODE_GENERATION_FAILED = "LICENSE_CODE_GENERATION_FAILED";
    public static final String LICENSE_CODE_SUBSCRIPTION_NOT_FOUND = "LICENSE_CODE_SUBSCRIPTION_NOT_FOUND";

    public static final int DEFAULT_PENDING_ORDER_TTL_HOURS = 24;
    public static final int DEFAULT_PAYMENT_LINK_TTL_HOURS = 24;
    public static final String PAYOS_PAYMENT_SUCCESS_CODE = "00";
    public static final String PAYOS_CURRENCY = "VND";
    public static final String PAYOS_EXPIRATION_CANCELLATION_REASON = "Payment link expired";
    public static final int DEFAULT_LICENSE_CODE_REDEEM_RATE_PER_SECOND = 5;

    public static final String FREE_STUDENT_LIMIT_SETTING_KEY = "free_student_limit";
    public static final String SUSPENSION_DEFAULT_DAYS_SETTING_KEY = "suspension_default_days";

    public static final String ORG_NAME_REQUIRED = "Organization name is required";
    public static final String ORG_TYPE_REQUIRED = "Organization type is required";
    public static final String REQUESTED_CODE_REQUIRED = "Requested code is required";
    public static final String REQUESTED_CODE_INVALID =
            "Requested code must be 3-32 lowercase letters, digits, or hyphens";
    public static final String CONTACT_EMAIL_REQUIRED = "Contact email is required";
    public static final String CONTACT_EMAIL_INVALID = "Contact email must be valid";
    public static final String REJECT_REASON_REQUIRED = "Reject reason is required";
    public static final String PLAN_NAME_REQUIRED = "Plan name is required";
    public static final String PLAN_PRICE_REQUIRED = "Plan price is required";
    public static final String PLAN_PRICE_NON_NEGATIVE = "Plan price must not be negative";
    public static final String PLAN_PRICE_PRECISION_INVALID =
            "Plan price must have at most 17 integer digits and 2 decimal places";
    public static final String PLAN_CURRENCY_REQUIRED = "Plan currency is required";
    public static final String PLAN_CURRENCY_INVALID = "Plan currency must be a 3-letter code";
    public static final String PLAN_TYPE_REQUIRED = "Plan type is required";
    public static final String PLAN_DESCRIPTION_MAX = "Plan description must be at most 255 characters";
    public static final String EXAM_DURATION_REQUIRED = "EXAM_PACKAGE requires durationDays > 0";
    public static final String EXAM_MAX_STUDENTS_REQUIRED =
            "EXAM_PACKAGE requires maxStudentsPerSession > 0";
    public static final String EXAM_EXTRA_STUDENT_SLOTS_FORBIDDEN =
            "EXAM_PACKAGE must not set extraStudentSlots";
    public static final String CAPACITY_EXTRA_STUDENTS_REQUIRED =
            "STUDENT_CAPACITY requires extraStudentSlots > 0";
    public static final String CAPACITY_DURATION_FORBIDDEN =
            "STUDENT_CAPACITY must not set durationDays";
    public static final String CAPACITY_MAX_STUDENTS_FORBIDDEN =
            "STUDENT_CAPACITY must not set maxStudentsPerSession";
    public static final String SETTING_KEY_REQUIRED = "Setting key is required";
    public static final String SETTING_VALUE_REQUIRED = "Setting value is required";
    public static final String SETTING_VALUE_MAX = "Setting value must be at most 255 characters";
    public static final String SETTING_DESCRIPTION_MAX = "Setting description must be at most 255 characters";

    private BillingConstants() {
    }
}
