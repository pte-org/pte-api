package com.pte.session.domain.enums;

public enum ReusePolicy {
    ALLOW,
    EXCLUDE_STARTED_IN_SERIES,
    EXCLUDE_ASSIGNED_IN_SERIES,
    BLOCK_ON_SCHEDULE_OVERLAP
}
