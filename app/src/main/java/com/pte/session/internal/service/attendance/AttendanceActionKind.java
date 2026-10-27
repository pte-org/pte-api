package com.pte.session.internal.service.attendance;

import com.pte.session.internal.constant.AttendanceConstants;

/** The two registration commands that share one window policy but word their rejections differently. */
enum AttendanceActionKind {
    ATTENDANCE(AttendanceConstants.LABEL_ATTENDANCE_RECORD, AttendanceConstants.LABEL_ATTENDANCE_MODIFY,
            AttendanceConstants.LABEL_ATTENDANCE_RECORDING, AttendanceConstants.LABEL_ATTENDANCE_WINDOW,
            AttendanceConstants.KEY_PREFIX_ATTENDANCE),
    IDENTITY_VERIFICATION(AttendanceConstants.LABEL_IDENTITY, AttendanceConstants.LABEL_IDENTITY,
            AttendanceConstants.LABEL_IDENTITY, AttendanceConstants.LABEL_IDENTITY_WINDOW,
            AttendanceConstants.KEY_PREFIX_IDENTITY);

    private final String recordLabel;
    private final String hostLabel;
    private final String proctorLabel;
    private final String windowLabel;
    private final String keyPrefix;

    AttendanceActionKind(String recordLabel, String hostLabel, String proctorLabel, String windowLabel,
            String keyPrefix) {
        this.recordLabel = recordLabel;
        this.hostLabel = hostLabel;
        this.proctorLabel = proctorLabel;
        this.windowLabel = windowLabel;
        this.keyPrefix = keyPrefix;
    }

    String recordLabel() {
        return recordLabel;
    }

    String hostLabel() {
        return hostLabel;
    }

    String proctorLabel() {
        return proctorLabel;
    }

    String windowLabel() {
        return windowLabel;
    }

    String keyPrefix() {
        return keyPrefix;
    }
}
