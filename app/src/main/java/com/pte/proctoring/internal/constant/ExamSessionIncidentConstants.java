package com.pte.proctoring.internal.constant;

import java.util.Set;

/** Error messages and workflow values owned by exam-session incidents. */
public final class ExamSessionIncidentConstants {

    public static final String AUTHENTICATED_CALLER_REQUIRED = "Authenticated caller is required";
    public static final String PROCTOR_TENANT_CONTEXT_REQUIRED = "Proctor tenant context is required";
    public static final String HOST_TENANT_CONTEXT_REQUIRED = "Host tenant context is required";
    public static final String PROCTOR_NOT_ASSIGNED = "Proctor is not assigned to this exam session";
    public static final String INCIDENT_ACCESS_DENIED = "Caller is not allowed to access exam session incidents";
    public static final String STUDENT_NOT_ENROLLED = "Student is not enrolled in this exam session";
    public static final String ATTEMPT_NOT_IN_SESSION = "Attempt does not belong to this exam session or student";
    public static final String INCIDENT_NOT_FOUND = "Incident not found";
    public static final String INVALID_STATUS =
            "Status must be one of NEW, IN_PROGRESS, ESCALATED, RESOLVED, CLOSED";
    public static final String CLOSED_INCIDENT_IMMUTABLE = "Closed incidents cannot be modified";
    public static final String PROCTOR_CANNOT_MODIFY_STATUS = "Proctor cannot modify incidents that are %s";
    public static final String PROCTOR_STATUS_TRANSITION_FORBIDDEN =
            "Proctor is not authorized to escalate, resolve or close incidents";
    public static final String PROCTOR_CANNOT_REOPEN = "Cannot transition incident from IN_PROGRESS back to NEW";
    public static final String PROCTOR_ROUTE_ONLY_HOST = "Proctor can only route incidents to HOST_ADMIN";
    public static final String INVALID_RECEIVER_ROLE =
            "Invalid receiver role. Must be one of: HOST_ADMIN, PLATFORM_ADMIN, PROCTOR";

    public static final String UNKNOWN_ACTOR_ROLE = "UNKNOWN";
    public static final String SYSTEM_ACTOR_NAME = "SYSTEM";
    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_ESCALATED = "ESCALATED";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_CLOSED = "CLOSED";
    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE_STATUS = "UPDATE_STATUS";
    public static final String OWNER_HOST_ADMIN = "HOST_ADMIN";
    public static final String OWNER_PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String OWNER_PROCTOR = "PROCTOR";

    public static final Set<String> VALID_STATUSES = Set.of(
            STATUS_NEW, STATUS_IN_PROGRESS, STATUS_ESCALATED, STATUS_RESOLVED, STATUS_CLOSED);
    public static final Set<String> ALLOWED_RECEIVER_ROLES = Set.of(
            OWNER_HOST_ADMIN, OWNER_PLATFORM_ADMIN, OWNER_PROCTOR);

    private ExamSessionIncidentConstants() {
    }
}
