package com.pte.session.internal.service.attendance;

import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.constant.AttendanceConstants;
import com.pte.session.internal.exception.attendance.AttendanceWindowClosedException;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** When a host or a proctor may still change attendance or identity state of a session. */
@Component
class AttendanceWindowPolicy {

    /** Rejects the command unless the caller's role may write in the session's current status and time window. */
    void requireWritable(ExamSession session, boolean isHost, Instant now, AttendanceActionKind kind) {
        SessionStatus status = session.getStatus();
        if (status == SessionStatus.CANCELLED) {
            throw new AttendanceWindowClosedException(
                    AttendanceConstants.MSG_CANCELLED.formatted(kind.recordLabel()));
        }
        if (isHost) {
            if (status != SessionStatus.SCHEDULED && status != SessionStatus.OPEN && status != SessionStatus.CLOSED) {
                throw new AttendanceWindowClosedException(
                        AttendanceConstants.MSG_HOST_STATUS.formatted(kind.hostLabel()));
            }
            return;
        }
        if (status != SessionStatus.SCHEDULED && status != SessionStatus.OPEN) {
            throw new AttendanceWindowClosedException(
                    AttendanceConstants.MSG_PROCTOR_STATUS.formatted(kind.proctorLabel()));
        }
        Instant opensAt = session.getOpensAt();
        Instant closesAt = session.getClosesAt();
        if (opensAt == null || closesAt == null) {
            throw new AttendanceWindowClosedException(AttendanceConstants.MSG_TIMING_MISSING);
        }
        if (now.isBefore(opensAt.minus(AttendanceConstants.PROCTOR_LEAD_TIME))) {
            throw new AttendanceWindowClosedException(
                    AttendanceConstants.MSG_BEFORE_WINDOW.formatted(kind.windowLabel()));
        }
        if (!now.isBefore(closesAt)) {
            throw new AttendanceWindowClosedException(
                    AttendanceConstants.MSG_AFTER_CLOSE.formatted(kind.windowLabel()));
        }
    }
}
