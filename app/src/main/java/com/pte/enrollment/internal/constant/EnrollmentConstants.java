package com.pte.enrollment.internal.constant;

/** Error and audit action codes owned by the enrollment module. */
public final class EnrollmentConstants {

    public static final String PROGRAM_NOT_FOUND = "PROGRAM_NOT_FOUND";
    public static final String PROGRAM_NAME_ALREADY_USED = "PROGRAM_NAME_ALREADY_USED";
    public static final String PROGRAM_HAS_ACTIVE_CLASSES = "PROGRAM_HAS_ACTIVE_CLASSES";
    public static final String ORGANIZATION_NOT_FOUND = "ORGANIZATION_NOT_FOUND";
    public static final String CLASS_NOT_FOUND = "CLASS_NOT_FOUND";
    public static final String CLASS_NAME_ALREADY_USED = "CLASS_NAME_ALREADY_USED";
    public static final String CLASS_HAS_ACTIVE_MEMBERS = "CLASS_HAS_ACTIVE_MEMBERS";
    public static final String STUDENT_ALREADY_IN_CLASS = "STUDENT_ALREADY_IN_CLASS";
    public static final String CLASS_MEMBERSHIP_NOT_FOUND = "CLASS_MEMBERSHIP_NOT_FOUND";
    public static final String LECTURER_ALREADY_ASSIGNED = "LECTURER_ALREADY_ASSIGNED";
    public static final String LECTURER_ASSIGNMENT_NOT_FOUND = "LECTURER_ASSIGNMENT_NOT_FOUND";
    public static final String COORDINATOR_ALREADY_ASSIGNED = "COORDINATOR_ALREADY_ASSIGNED";
    public static final String COORDINATOR_ASSIGNMENT_NOT_FOUND = "COORDINATOR_ASSIGNMENT_NOT_FOUND";
    public static final String INVALID_STUDENT_ROSTER_QUERY = "INVALID_STUDENT_ROSTER_QUERY";
    public static final String STUDENT_NOT_FOUND = "STUDENT_NOT_FOUND";

    public static final String AGGREGATE_PROGRAM = "Program";
    public static final String EVENT_PROGRAM_CREATED = "ProgramCreated";
    public static final String EVENT_PROGRAM_UPDATED = "ProgramUpdated";
    public static final String EVENT_PROGRAM_STATUS_CHANGED = "ProgramStatusChanged";
    public static final String EVENT_PROGRAM_ARCHIVED = "ProgramArchived";

    public static final String AGGREGATE_CLASS = "StudentClass";
    public static final String EVENT_CLASS_CREATED = "ClassCreated";
    public static final String EVENT_CLASS_UPDATED = "ClassUpdated";
    public static final String EVENT_CLASS_STATUS_CHANGED = "ClassStatusChanged";
    public static final String EVENT_CLASS_ARCHIVED = "ClassArchived";
    public static final String EVENT_STUDENT_ASSIGNED_TO_CLASS = "StudentAssignedToClass";
    public static final String EVENT_STUDENT_UNASSIGNED_FROM_CLASS = "StudentUnassignedFromClass";
    public static final String EVENT_STUDENT_TRANSFERRED_CLASS = "StudentTransferredClass";
    public static final String EVENT_CLASSES_MERGED = "ClassesMerged";
    public static final String EVENT_CLASS_SPLIT = "ClassSplit";
    public static final String EVENT_LECTURER_ASSIGNED = "LecturerAssigned";
    public static final String EVENT_LECTURER_UNASSIGNED = "LecturerUnassigned";
    public static final String EVENT_COORDINATOR_ASSIGNED = "CoordinatorAssigned";
    public static final String EVENT_COORDINATOR_UNASSIGNED = "CoordinatorUnassigned";

    private EnrollmentConstants() {
    }
}
