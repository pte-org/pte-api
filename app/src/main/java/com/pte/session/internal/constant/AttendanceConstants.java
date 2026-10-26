package com.pte.session.internal.constant;

import java.time.Duration;

/** Messages, audit names and rules of attendance and identity verification. */
public final class AttendanceConstants {

    /** Proctors may record from this long before the session opens. */
    public static final Duration PROCTOR_LEAD_TIME = Duration.ofMinutes(30);

    public static final String ACTOR_ROLE_HOST = "HOST_ADMIN";
    public static final String ACTOR_ROLE_PROCTOR = "PROCTOR";

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_NO_OP = "NO_OP";

    /** Audit aggregate of one student's attendance in one session; the id is {@code sessionId:studentId}. */
    public static final String AUDIT_AGGREGATE_ATTENDANCE = "SESSION_ATTENDANCE";
    public static final String AUDIT_ACTION_ATTENDANCE = "ATTENDANCE_RECORDED";
    public static final String AUDIT_ACTION_IDENTITY = "IDENTITY_VERIFIED";
    public static final String AUDIT_ID_SEPARATOR = ":";
    public static final String AUDIT_SUMMARY_ATTENDANCE = "Attendance %s -> %s";
    public static final String AUDIT_SUMMARY_IDENTITY = "Identity %s -> %s";

    public static final String KEY_PREFIX_ATTENDANCE = "ATTENDANCE";
    public static final String KEY_PREFIX_IDENTITY = "IDENTITY";
    public static final String KEY_SEPARATOR = ":";

    public static final String BLOCK_NOT_ENROLLED = "NOT_ENROLLED";
    public static final String BLOCK_SESSION_CANCELLED = "SESSION_CANCELLED";
    public static final String BLOCK_SESSION_NOT_MODIFIABLE = "SESSION_NOT_MODIFIABLE";
    public static final String BLOCK_SESSION_CLOSED = "SESSION_CLOSED";
    public static final String BLOCK_BEFORE_WINDOW = "BEFORE_ATTENDANCE_WINDOW";
    public static final String BLOCK_NOT_SCHEDULED_OR_OPEN = "SESSION_NOT_SCHEDULED_OR_OPEN";

    public static final String LABEL_ATTENDANCE_RECORD = "attendance";
    public static final String LABEL_ATTENDANCE_MODIFY = "attendance modification";
    public static final String LABEL_ATTENDANCE_RECORDING = "attendance recording";
    public static final String LABEL_IDENTITY = "identity verification";
    public static final String LABEL_ATTENDANCE_WINDOW = "Attendance recording";
    public static final String LABEL_IDENTITY_WINDOW = "Identity verification";

    public static final String MSG_CANCELLED = "Cannot record %s for a cancelled session";
    public static final String MSG_HOST_STATUS = "Host %s is only permitted for SCHEDULED, OPEN, or CLOSED sessions";
    public static final String MSG_PROCTOR_STATUS = "Proctor %s is only allowed for SCHEDULED or OPEN sessions";
    public static final String MSG_TIMING_MISSING = "Session timing is missing";
    public static final String MSG_BEFORE_WINDOW = "%s is not permitted before 30 minutes prior to session opening";
    public static final String MSG_AFTER_CLOSE = "%s is closed after session closing time";
    public static final String MSG_HOST_REASON_ATTENDANCE = "Reason is mandatory for Host attendance actions";
    public static final String MSG_HOST_REASON_IDENTITY =
            "Reason is mandatory for Host identity verification on closed session";
    public static final String MSG_PROCTOR_REASON_CHANGE =
            "Reason is required when changing marked attendance status or resetting to UNMARKED";
    public static final String MSG_GENERATION_MISMATCH =
            "Enrollment generation mismatch: request targeted %s but active enrollment is %s";
    public static final String MSG_ATTENDANCE_VERSION_CONFLICT =
            "Attendance version conflict: expected %s but current version is %s";
    public static final String MSG_IDENTITY_VERSION_CONFLICT =
            "Identity verification version conflict: expected %s but current version is %s";
    public static final String MSG_REQUEST_ID_REUSED = "Request ID %s already used with a different command intent";
    public static final String MSG_RECEIPT_NOT_FOUND = "Attendance command receipt not found for request %s";
    public static final String MSG_REQUEST_ID_REQUIRED = "requestPublicId is required";
    public static final String MSG_EXPECTED_VERSION_REQUIRED = "expectedVersion is required";
    public static final String MSG_STATUS_REQUIRED = "status is required";
    public static final String MSG_IDENTITY_STATUS_INVALID =
            "Identity status must be one of: UNVERIFIED, VERIFIED, FAILED";

    private AttendanceConstants() {
    }
}
