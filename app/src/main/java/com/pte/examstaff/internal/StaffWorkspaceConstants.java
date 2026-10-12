package com.pte.examstaff.internal;

import java.util.Set;

final class StaffWorkspaceConstants {
    static final String INVALID_QUERY = "INVALID_EXAM_STAFF_QUERY";
    static final String FORBIDDEN_WORK = "EXAM_STAFF_WORK_FORBIDDEN";
    static final int SCAN_SIZE = 100;
    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;
    static final Set<String> WORK_STATUSES = Set.of("ALL", "PENDING", "IN_PROGRESS", "COMPLETED", "UNAVAILABLE");
    static final Set<String> PUBLICATION_STATUSES = Set.of("ALL", "PUBLISHED", "UNPUBLISHED");
    static final Set<String> SESSION_STATUSES = Set.of("DRAFT", "PREPARING", "READY", "SCHEDULED", "OPEN", "CLOSED", "CANCELLED");
    private StaffWorkspaceConstants() { }
}
