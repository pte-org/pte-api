package com.pte.session.internal.constant;

/** Centralized codes/labels for session. */
public final class SessionConstants {

    public static final String SESSION_NOT_FOUND = "SESSION_NOT_FOUND";
    public static final String TASK_TYPE_NOT_IN_SNAPSHOT = "TASK_TYPE_NOT_IN_SNAPSHOT";
    public static final String ALREADY_ENROLLED = "ALREADY_ENROLLED";
    public static final String ALREADY_ASSIGNED = "ALREADY_ASSIGNED";
    public static final String ENROLLMENT_NOT_FOUND = "ENROLLMENT_NOT_FOUND";
    public static final String PROCTOR_ASSIGNMENT_NOT_FOUND = "PROCTOR_ASSIGNMENT_NOT_FOUND";
    public static final String HOST_CONTEXT_REQUIRED = "HOST_CONTEXT_REQUIRED";
    public static final String INVALID_SESSION_WINDOW = "INVALID_SESSION_WINDOW";
    public static final String NOT_ENTITLED = "NOT_ENTITLED";
    public static final String PROCTOR_NOT_ASSIGNED = "PROCTOR_NOT_ASSIGNED";
    public static final String POLICY_LOCKED = "POLICY_LOCKED";
    public static final String INVALID_POLICY_PATCH = "INVALID_POLICY_PATCH";
    public static final String SESSION_CAPACITY_EXCEEDED = "SESSION_CAPACITY_EXCEEDED";

    public static final String PROCTOR_REFERENCE_REQUIRED = "Proctor reference is required";
    public static final String AT_LEAST_ONE_STUDENT_REQUIRED = "At least one student is required";
    public static final String TASK_TYPE_REQUIRED = "Task type is required";
    public static final String SECTION_REQUIRED = "Section is required";
    public static final String TIMING_OVERRIDE_POSITIVE = "Timing override must be positive if provided";
    public static final String MAX_PLAY_COUNT_POSITIVE = "Max play count must be positive if provided";
    public static final String SESSION_NAME_REQUIRED = "Session name is required";
    public static final String SNAPSHOT_REFERENCE_REQUIRED = "Snapshot reference is required";
    public static final String OPEN_TIME_REQUIRED = "Open time is required";
    public static final String OPEN_TIME_FUTURE = "Open time must be in the future";
    public static final String CLOSE_TIME_REQUIRED = "Close time is required";
    public static final String CAPACITY_POSITIVE = "Capacity must be positive";
    public static final String STUDENT_REFERENCE_REQUIRED = "Student reference is required";
    public static final String REPLAY_POLICY_LIMIT_POSITIVE = "Replay policy limit must be positive if provided";
    public static final String COMPOSITION_ITEMS_REQUIRED = "Composition needs at least one item";
    public static final String ROLE_REQUIRED = "Role is required";

    public static final String LIMITED_REPLAY_COUNT_INVALID = "Limited replay count must be >= 1";
    public static final String EXAM_POLICY_INCOMPLETE = "ExamPolicy is incomplete — expected all fields set together";
    public static final String STRICT_LOCKDOWN_NOT_ALLOWED_FOR_PRACTICE = "LockdownMode.STRICT is not allowed for PRACTICE exams";

    private SessionConstants() {
    }
}
