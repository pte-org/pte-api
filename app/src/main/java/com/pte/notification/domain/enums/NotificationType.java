package com.pte.notification.domain.enums;

public enum NotificationType {
    STUDENT_ENROLLED,
    ATTEMPT_PUBLISHED,
    VIOLATION_DETECTED,
    SESSION_CANCELLED,
    STUDENT_ENROLLMENT_CONFLICT,
    TENANT_APPLICATION_SUBMITTED,
    TENANT_APPLICATION_APPROVED,
    TENANT_APPLICATION_REJECTED,
    USER_CREDENTIALS_SENT,
    /** No longer sent (practice signs in with roster passwords); kept so historical log rows still load. */
    PRACTICE_EMAIL_CHALLENGE
}
