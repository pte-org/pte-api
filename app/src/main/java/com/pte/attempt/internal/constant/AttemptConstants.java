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

    public static final String CACHE_KEY_PREFIX = "attempt:pinned-snapshot:";
    public static final String LOCK_KEY_PREFIX = "attempt:lock:pinned-snapshot:";

    private AttemptConstants() {
    }
}
