package com.pte.billing.internal.constant;

/** Error codes and validation messages owned by the billing module. */
public final class BillingConstants {

    public static final String LICENSE_ISSUE_OPERATION = "ISSUE_LICENSE_CODE";
    public static final String LICENSE_ISSUE_INTENT_CONSTRAINT = "uk_license_issue_intents_actor_operation_key";
    public static final String LICENSE_CODE_IDEMPOTENCY_KEY_REQUIRED = "LICENSE_CODE_IDEMPOTENCY_KEY_REQUIRED";
    public static final String LICENSE_CODE_IDEMPOTENCY_KEY_INVALID = "LICENSE_CODE_IDEMPOTENCY_KEY_INVALID";
    public static final String LICENSE_CODE_IDEMPOTENCY_KEY_REQUIRED_MESSAGE = "Reload this page before issuing a license code.";
    public static final String LICENSE_CODE_IDEMPOTENCY_KEY_INVALID_MESSAGE = "Idempotency-Key must be a UUID. Reload this page and try again.";
    public static final String LICENSE_CODE_IDEMPOTENCY_KEY_REUSED = "LICENSE_CODE_IDEMPOTENCY_KEY_REUSED";
    public static final String LICENSE_CODE_ISSUE_RESULT_MISSING = "LICENSE_CODE_ISSUE_RESULT_MISSING";
    public static final String LICENSE_CODE_ISSUE_RETRYABLE = "LICENSE_CODE_ISSUE_RETRYABLE";
    public static final String LICENSE_CODE_ISSUE_FAILED = "LICENSE_CODE_ISSUE_FAILED";
    public static final String LICENSE_CODE_EXAM_PLAN_REQUIRED = "LICENSE_CODE_EXAM_PLAN_REQUIRED";
    public static final String LICENSE_CODE_EXPIRY_PRECISION_INVALID = "LICENSE_CODE_EXPIRY_PRECISION_INVALID";

    public static final String PLAN_DELETE_DRAFT_ONLY = "PLAN_DELETE_DRAFT_ONLY";
    public static final String PLAN_HAS_REFERENCES = "PLAN_HAS_REFERENCES";
    public static final String PLAN_HAS_OUTSTANDING_CODES = "PLAN_HAS_OUTSTANDING_CODES";
    public static final String PLAN_MUST_BE_ACTIVE_TO_ARCHIVE = "PLAN_MUST_BE_ACTIVE_TO_ARCHIVE";
    public static final String PLAN_ACTIVE_TYPE_IMMUTABLE = "PLAN_ACTIVE_TYPE_IMMUTABLE";
    public static final String PLAN_DELETE_DRAFT_ONLY_MESSAGE = "Only an unused draft plan can be deleted.";
    public static final String PLAN_HAS_REFERENCES_MESSAGE = "This plan has billing history and cannot be deleted.";
    public static final String PLAN_HAS_OUTSTANDING_CODES_MESSAGE = "This plan has unredeemed license codes. Revoke or wait for them to expire before archiving or changing its benefits.";
    public static final String PLAN_MUST_BE_ACTIVE_TO_ARCHIVE_MESSAGE = "Only active plans can be archived. Delete unused drafts instead.";
    public static final String PLAN_ACTIVE_TYPE_IMMUTABLE_MESSAGE = "Create a new plan to change the type of an active plan.";
    public static final String PLAN_AGGREGATE = "Plan";
    public static final String PLAN_DRAFT_DELETED = "PlanDraftDeleted";
    public static final String PLAN_DRAFT_DELETED_SUMMARY = "Unused plan draft removed.";

    public static final String TENANT_APPLICATION_NOT_FOUND = "TENANT_APPLICATION_NOT_FOUND";
    public static final String TENANT_APPLICATION_INVALID = "TENANT_APPLICATION_INVALID";
    public static final String REQUESTED_CODE_ALREADY_USED = "REQUESTED_CODE_ALREADY_USED";
    public static final String TENANT_NAME_ALREADY_USED = "TENANT_NAME_ALREADY_USED";
    public static final String TENANT_TAX_CODE_ALREADY_USED = "TENANT_TAX_CODE_ALREADY_USED";
    public static final String APPLICATION_NOT_PENDING = "APPLICATION_NOT_PENDING";
    public static final String PLAN_NOT_FOUND = "PLAN_NOT_FOUND";
    public static final String PLAN_TYPE_INVALID = "PLAN_TYPE_INVALID";
    public static final String PLAN_MUST_BE_DRAFT_TO_ACTIVATE = "PLAN_MUST_BE_DRAFT_TO_ACTIVATE";
    public static final String PLAN_ALREADY_ARCHIVED = "PLAN_ALREADY_ARCHIVED";
    public static final String PLAN_ARCHIVED_NOT_EDITABLE = "PLAN_ARCHIVED_NOT_EDITABLE";
    public static final String PLAN_VERSION_CONFLICT = "PLAN_VERSION_CONFLICT";
    public static final String PLATFORM_SETTING_NOT_FOUND = "PLATFORM_SETTING_NOT_FOUND";
    public static final String PLATFORM_SETTING_INVALID = "PLATFORM_SETTING_INVALID";
    public static final String SUBSCRIPTION_TENANT_REQUIRED = "SUBSCRIPTION_TENANT_REQUIRED";
    public static final String SUBSCRIPTION_PLAN_REQUIRED = "SUBSCRIPTION_PLAN_REQUIRED";
    public static final String SUBSCRIPTION_SOURCE_REQUIRED = "SUBSCRIPTION_SOURCE_REQUIRED";
    public static final String SUBSCRIPTION_PLAN_TYPE_INVALID = "SUBSCRIPTION_PLAN_TYPE_INVALID";
    public static final String SUBSCRIPTION_PLAN_NOT_ACTIVE = "SUBSCRIPTION_PLAN_NOT_ACTIVE";
    public static final String SUBSCRIPTION_EXAM_FIELDS_INVALID = "SUBSCRIPTION_EXAM_FIELDS_INVALID";
    public static final String SUBSCRIPTION_CAPACITY_FIELDS_INVALID = "SUBSCRIPTION_CAPACITY_FIELDS_INVALID";
    public static final String SUBSCRIPTION_NOT_FOUND = "SUBSCRIPTION_NOT_FOUND";
    public static final String LICENSE_KEY_REVEAL_INVALID_PASSWORD = "LICENSE_KEY_REVEAL_INVALID_PASSWORD";
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
    public static final String LICENSE_CODE_REVOKE_SCOPE_CHANGED = "LICENSE_CODE_REVOKE_SCOPE_CHANGED";
    public static final String LICENSE_CODE_REVOKE_PREVIEW_EXPIRED = "LICENSE_CODE_REVOKE_PREVIEW_EXPIRED";
    public static final String LICENSE_CODE_REVOKE_CONFIRMATION_REQUIRED =
            "LICENSE_CODE_REVOKE_CONFIRMATION_REQUIRED";
    public static final String LICENSE_CODE_REVOKE_CAPACITY_UNSUPPORTED =
            "LICENSE_CODE_REVOKE_CAPACITY_UNSUPPORTED";
    public static final String LICENSE_CODE_REVOKE_TENANT_MISMATCH =
            "LICENSE_CODE_REVOKE_TENANT_MISMATCH";
    public static final String LICENSE_CODE_REVOKE_IMPACT_UNAVAILABLE =
            "LICENSE_CODE_REVOKE_IMPACT_UNAVAILABLE";
    public static final String LICENSE_CODE_REVOKE_LEGACY_ENDPOINT =
            "LICENSE_CODE_REVOKE_LEGACY_ENDPOINT";
    public static final String LICENSE_CODE_LEGACY_LIST_ENDPOINT =
            "LICENSE_CODE_LEGACY_LIST_ENDPOINT";
    public static final String LICENSE_CODE_LEGACY_LIST_ENDPOINT_MESSAGE =
            "Update the admin portal to use the bounded license-code view.";
    public static final String LICENSE_CODE_STATUS_INVALID = "LICENSE_CODE_STATUS_INVALID";
    public static final String LICENSE_CODE_PAGE_INVALID = "LICENSE_CODE_PAGE_INVALID";
    public static final String LICENSE_CODE_PAGE_SIZE_INVALID = "LICENSE_CODE_PAGE_SIZE_INVALID";
    public static final String LICENSE_CODE_LOOKUP_NOT_FOUND = "LICENSE_CODE_LOOKUP_NOT_FOUND";
    public static final String LICENSE_CODE_REVEAL_AUDIT = "LicenseCodeRevealed";
    public static final String LICENSE_CODE_REVEAL_AUDIT_SUMMARY = "License bearer revealed by an authorized platform administrator.";

    public static final int DEFAULT_PENDING_ORDER_TTL_HOURS = 24;
    public static final int DEFAULT_PAYMENT_LINK_TTL_HOURS = 24;
    public static final String PAYOS_PAYMENT_SUCCESS_CODE = "00";
    public static final String PAYOS_CURRENCY = "VND";
    public static final String PAYOS_EXPIRATION_CANCELLATION_REASON = "Payment link expired";
    public static final int DEFAULT_LICENSE_CODE_REDEEM_RATE_PER_SECOND = 5;
    public static final int MAX_STUDENT_COUNT = 2_000;

    public static final String FREE_STUDENT_LIMIT_SETTING_KEY = "free_student_limit";
    public static final String SUSPENSION_DEFAULT_DAYS_SETTING_KEY = "suspension_default_days";

    public static final String ORG_NAME_REQUIRED = "Organization name is required";
    public static final String ORG_TYPE_REQUIRED = "Organization type is required";
    public static final String REQUESTED_CODE_REQUIRED = "Requested code is required";
    public static final String REQUESTED_CODE_INVALID =
            "Requested code must be 3-32 lowercase letters, digits, or hyphens";
    public static final String CONTACT_EMAIL_REQUIRED = "Contact email is required";
    public static final String CONTACT_EMAIL_INVALID = "Contact email must be valid";
    public static final String TAX_CODE_REQUIRED = "Tax code is required";
    public static final String TAX_CODE_MAX = "Tax code must be at most 64 characters";
    public static final String REJECT_REASON_REQUIRED = "Reject reason is required";
    public static final String REJECT_REASON_MAX = "Reject reason must be at most 500 characters";
    public static final String ORG_NAME_MAX = "Organization name must be at most 255 characters";
    public static final String ORG_TYPE_MAX = "Organization type must be at most 255 characters";
    public static final String CONTACT_EMAIL_MAX = "Contact email must be at most 255 characters";
    public static final String CONTACT_PHONE_MAX = "Contact phone must be at most 255 characters";
    public static final String PLAN_NAME_REQUIRED = "Plan name is required";
    public static final String PLAN_NAME_MAX = "Plan name must be at most 255 characters";
    public static final String PLAN_PRICE_REQUIRED = "Plan price is required";
    public static final String PLAN_PRICE_NON_NEGATIVE = "Plan price must not be negative";
    public static final String PLAN_PRICE_PRECISION_INVALID =
            "Plan price must have at most 17 integer digits and 2 decimal places";
    public static final String PLAN_CURRENCY_REQUIRED = "Plan currency is required";
    public static final String PLAN_CURRENCY_INVALID = "Plan currency must be VND";
    public static final String PLAN_VERSION_REQUIRED = "Plan version is required";
    public static final String PLAN_VERSION_INVALID = "Plan version must be a non-negative integer";
    public static final String PLAN_VERSION_CONFLICT_MESSAGE = "This plan changed while you were editing. Reload it and try again.";
    public static final String PLAN_TYPE_REQUIRED = "Plan type is required";
    public static final String PLAN_DESCRIPTION_MAX = "Plan description must be at most 255 characters";
    public static final String EXAM_DURATION_REQUIRED = "EXAM_PACKAGE requires durationDays > 0";
    public static final String EXAM_DURATION_LIMIT_EXCEEDED = "durationDays must not exceed 3650";
    public static final String EXAM_MAX_STUDENTS_REQUIRED =
            "EXAM_PACKAGE requires maxStudentsPerSession > 0";
    public static final String EXAM_MAX_STUDENTS_LIMIT_EXCEEDED =
            "maxStudentsPerSession must not exceed " + MAX_STUDENT_COUNT;
    public static final String EXAM_EXTRA_STUDENT_SLOTS_FORBIDDEN =
            "EXAM_PACKAGE must not set extraStudentSlots";
    public static final String CAPACITY_EXTRA_STUDENTS_REQUIRED =
            "STUDENT_CAPACITY requires extraStudentSlots > 0";
    public static final String CAPACITY_EXTRA_STUDENTS_LIMIT_EXCEEDED =
            "extraStudentSlots must not exceed " + MAX_STUDENT_COUNT;
    public static final String CAPACITY_DURATION_FORBIDDEN =
            "STUDENT_CAPACITY must not set durationDays";
    public static final String CAPACITY_MAX_STUDENTS_FORBIDDEN =
            "STUDENT_CAPACITY must not set maxStudentsPerSession";
    public static final String SETTING_KEY_REQUIRED = "Setting key is required";
    public static final String SETTING_VALUE_REQUIRED = "Setting value is required";
    public static final String SETTING_VALUE_MAX = "Setting value must be at most 255 characters";
    public static final String SETTING_DESCRIPTION_MAX = "Setting description must be at most 255 characters";
    public static final String PASSWORD_REQUIRED = "Password is required";

    private BillingConstants() {
    }
}
