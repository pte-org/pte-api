package com.pte.attempt.internal.constant;

public final class AttemptConstants {

    public static final String ATTEMPT_NOT_FOUND = "ATTEMPT_NOT_FOUND";
    public static final String ALREADY_ATTEMPTED = "ALREADY_ATTEMPTED";
    public static final String TASK_TIMING_NOT_CONFIGURED = "TASK_TIMING_NOT_CONFIGURED";
    public static final String NOT_CURRENT_TASK = "NOT_CURRENT_TASK";
    public static final String ATTEMPT_ALREADY_COMPLETE = "ATTEMPT_ALREADY_COMPLETE";
    public static final String DEVICE_CHECK_REQUIRED = "DEVICE_CHECK_REQUIRED";
    public static final String MISSING_AUDIO_PROMPT = "MISSING_AUDIO_PROMPT";
    public static final String MISSING_AUDIO_DURATION = "MISSING_AUDIO_DURATION";
    public static final String REPLAY_LIMIT_EXCEEDED = "REPLAY_LIMIT_EXCEEDED";
    public static final String AUDIO_URL_EXPIRED = "AUDIO_URL_EXPIRED";
    public static final String SUBMISSION_DECRYPTION_FAILED = "SUBMISSION_DECRYPTION_FAILED";
    public static final String ANSWER_INTEGRITY_LEVEL_MISMATCH = "ANSWER_INTEGRITY_LEVEL_MISMATCH";
    public static final String MISSING_IMAGE_PROMPT = "MISSING_IMAGE_PROMPT";
    public static final String ANSWER_ALREADY_SUBMITTED = "ANSWER_ALREADY_SUBMITTED";
    public static final String PINNED_SNAPSHOT_EMPTY = "PINNED_SNAPSHOT_EMPTY";
    public static final String EXAM_REQUIRES_APP_UPDATE = "EXAM_REQUIRES_APP_UPDATE";
    public static final String EXAM_CONFIGURATION_NOT_COMPATIBLE = "EXAM_CONFIGURATION_NOT_COMPATIBLE";
    public static final String CAPABILITY_MANIFEST_INVALID = "CAPABILITY_MANIFEST_INVALID";
    public static final String UNSUPPORTED_RUNTIME_CONTRACT = "UNSUPPORTED_RUNTIME_CONTRACT";

    public static final String EXAM_REQUIRES_APP_UPDATE_MESSAGE =
            "Please update the PTE Prep app before starting this exam. It requires capabilities that this app does not provide.";
    public static final String EXAM_CONFIGURATION_NOT_COMPATIBLE_MESSAGE =
            "This exam is not currently compatible with the configured task runtime. Please ask an administrator to review the exam configuration.";
    public static final String CAPABILITY_MANIFEST_INVALID_MESSAGE =
            "The app capability manifest is invalid. Please update the app and try again.";
    public static final String UNSUPPORTED_RUNTIME_CONTRACT_MESSAGE =
            "This exam includes a task screen that this app version does not support. "
                    + "Update the app or contact your exam administrator. Your attempt was not advanced past this task.";
    public static final String RUNTIME_CONTRACT_FAILURE_AUDIT_ACTION = "RUNTIME_CONTRACT_FAILURE";
    public static final String RUNTIME_CONTRACT_FAILURE_AUDIT_SUMMARY =
            "Attempt delivery was blocked because the frozen exam runtime contract was incompatible";

    public static final String TASK_REFERENCE_REQUIRED = "Task reference is required";
    public static final String WRAPPED_KEY_REQUIRED = "Wrapped key is required";
    public static final String INITIALIZATION_VECTOR_REQUIRED = "Initialization vector is required";
    public static final String CIPHERTEXT_REQUIRED = "Ciphertext is required";
    public static final String SESSION_REFERENCE_REQUIRED = "Session reference is required";

    public static final String ENCRYPTION_KEYPAIR_NOT_CONFIGURED =
            "attempt encryption keypair is not configured (attempt.encryption.private-key-pem / public-key-pem) "
                    + "and active profile '%s' is not dev/local — refusing to start with an ephemeral keypair, "
                    + "since a restart would invalidate every STRICT-pinned attempt's public key.";
    public static final String EPHEMERAL_KEYPAIR_GENERATION_FAILED = "Failed to generate ephemeral RSA encryption keypair";
    public static final String PRIVATE_KEY_PARSE_FAILED = "Failed to parse attempt encryption private key PEM";
    public static final String PUBLIC_KEY_PARSE_FAILED = "Failed to parse attempt encryption public key PEM";
    public static final String TASK_TIMING_LOAD_FAILED = "Failed to load task timing from %s";
    public static final String PINNED_ITEM_OPTIONS_PARSE_FAILED = "Failed to parse pinned item optionsJson";
    public static final String MIXED_BLANK_INDEX_OPTIONS =
            "Pinned item %s has a mix of blank-grouped and ungrouped options — "
                    + "every option must either carry a blankIndex or none may.";
    public static final String MISSING_PINNED_ITEM_AT_INDEX = "Missing pinned item at index %s";
    public static final String ATTEMPT_DISAPPEARED_MID_TRANSACTION = "Attempt disappeared mid-transaction: %s";
    public static final String PINNED_SNAPSHOT_CACHE_SERIALIZATION_FAILED = "Failed to serialize pinned snapshot cache entry";

    public static final String CACHE_KEY_PREFIX = "attempt:pinned-snapshot:";
    public static final String LOCK_KEY_PREFIX = "attempt:lock:pinned-snapshot:";

    private AttemptConstants() {
    }
}
