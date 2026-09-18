package com.pte.session.internal.constant;

/** Centralized codes/labels for session. */
public final class SessionConstants {

    public static final String SESSION_NOT_FOUND = "SESSION_NOT_FOUND";
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
    public static final String CLASS_ASSIGNMENT_NOT_ALLOWED = "CLASS_ASSIGNMENT_NOT_ALLOWED";
    public static final String CLASS_ASSIGNMENT_NOT_FOUND = "CLASS_ASSIGNMENT_NOT_FOUND";
    public static final String SESSION_SUBSCRIPTION_NOT_FOUND = "SESSION_SUBSCRIPTION_NOT_FOUND";
    public static final String SESSION_WINDOW_OUTSIDE_SUBSCRIPTION = "SESSION_WINDOW_OUTSIDE_SUBSCRIPTION";
    public static final String SESSION_CAPACITY_EXCEEDS_SUBSCRIPTION = "SESSION_CAPACITY_EXCEEDS_SUBSCRIPTION";
    public static final String SESSION_ENROLLMENTS_EXCEED_SUBSCRIPTION = "SESSION_ENROLLMENTS_EXCEED_SUBSCRIPTION";
    public static final String SESSION_TIME_CONFLICT = "SESSION_TIME_CONFLICT";

    public static final String PROCTOR_REFERENCE_REQUIRED = "Proctor reference is required";
    public static final String AT_LEAST_ONE_STUDENT_REQUIRED = "At least one student is required";
    public static final String SESSION_NAME_REQUIRED = "Session name is required";
    public static final String SKILLS_REQUIRED = "At least one skill is required";
    public static final String SKILLS_SIZE_INVALID = "Between 1 and 4 skills must be selected";
    public static final String SUBSCRIPTION_REFERENCE_REQUIRED = "Subscription reference is required";
    public static final String OPEN_TIME_REQUIRED = "Open time is required";
    public static final String OPEN_TIME_FUTURE = "Open time must be in the future";
    public static final String CLOSE_TIME_REQUIRED = "Close time is required";
    public static final String CAPACITY_POSITIVE = "Capacity must be positive";
    public static final String CAPACITY_REQUIRED = "Capacity is required";
    public static final String STUDENT_REFERENCE_REQUIRED = "Student reference is required";
    public static final String REPLAY_POLICY_LIMIT_POSITIVE = "Replay policy limit must be positive if provided";
    public static final String ROLE_REQUIRED = "Role is required";
    public static final String CLASS_REFERENCE_REQUIRED = "Class reference is required";

    public static final String LIMITED_REPLAY_COUNT_INVALID = "Limited replay count must be >= 1";
    public static final String EXAM_POLICY_INCOMPLETE = "ExamPolicy is incomplete — expected all fields set together";
    public static final String STRICT_LOCKDOWN_NOT_ALLOWED_FOR_PRACTICE = "LockdownMode.STRICT is not allowed for PRACTICE exams";
    public static final String SESSION_CAPACITY_EXCEEDS_SUBSCRIPTION_DETAIL =
            "Session capacity %d exceeds subscription cap %d";
    public static final String SESSION_ENROLLMENTS_EXCEED_SUBSCRIPTION_DETAIL =
            "Enrollment count %d exceeds subscription cap %d";
    public static final String SESSION_TIME_CONFLICT_DETAIL =
            "Session time conflicts with existing session %s";

    private SessionConstants() {
    }
}
