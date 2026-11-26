package com.pte.shared.audit.internal.constant;

public final class AuditLogConstants {

    public static final String ROLE_SEPARATOR = ",";

    /**
     * Safety cap for one before/after snapshot. Measured business states stay far below it (a 22-task template is
     * about 5 KB, the largest question about 3 KB); a larger value is replaced by a truncation marker.
     */
    public static final int STATE_MAX_CHARS = 65_536;
    public static final int STATE_PREVIEW_CHARS = 2_000;
    public static final String STATE_TRUNCATED_KEY = "truncated";
    public static final String STATE_PREVIEW_KEY = "preview";

    public static final String AUDIT_LOG_NOT_FOUND = "AUDIT_LOG_NOT_FOUND";
    public static final String AUDIT_LOG_NOT_FOUND_MESSAGE = "This audit entry does not exist.";

    private AuditLogConstants() {
    }
}
