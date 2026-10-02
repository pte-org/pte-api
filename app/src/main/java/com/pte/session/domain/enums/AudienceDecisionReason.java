package com.pte.session.domain.enums;

public enum AudienceDecisionReason {
    DUPLICATE_SOURCE,
    ALREADY_ASSIGNED,
    ALREADY_STARTED,
    SCHEDULE_OVERLAP,
    OUTSIDE_TENANT,
    CAPACITY_EXCEEDED
}
