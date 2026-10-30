package com.pte.session.internal.service.staff;

import com.pte.session.domain.ExamSession;
import com.pte.session.domain.ExamSessionProctor;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.dto.response.ProctorMonitoringSessionView;
import com.pte.session.dto.response.ProctorWorkspaceSessionView;

import java.time.Instant;

/** Monitorability rules and view mapping for a proctor's assigned sessions. Pure, stateless. */
final class ProctorSessionViews {

    private ProctorSessionViews() {
    }

    static boolean canMonitor(ExamSession s, Instant asOf) {
        if (s == null || s.isDeleted() || s.getStatus() != SessionStatus.OPEN) {
            return false;
        }
        if (s.getOpensAt() == null || s.getClosesAt() == null) {
            return false;
        }
        return !asOf.isBefore(s.getOpensAt()) && asOf.isBefore(s.getClosesAt());
    }

    static String monitoringBlockingReason(ExamSession s, Instant asOf) {
        if (canMonitor(s, asOf)) {
            return null;
        }
        if (s == null) {
            return "SESSION_NOT_FOUND";
        }
        if (s.getStatus() == SessionStatus.CANCELLED) {
            return "SESSION_CANCELLED";
        }
        if (s.getStatus() == SessionStatus.CLOSED) {
            return "SESSION_CLOSED";
        }
        if (s.getStatus() != SessionStatus.OPEN && s.getStatus() != SessionStatus.SCHEDULED) {
            return "SESSION_NOT_OPEN";
        }
        if (s.getOpensAt() == null || s.getClosesAt() == null) {
            return "WINDOW_NOT_CONFIGURED";
        }
        if (asOf.isBefore(s.getOpensAt())) {
            return "WINDOW_NOT_OPENED";
        }
        if (!asOf.isBefore(s.getClosesAt())) {
            return "WINDOW_CLOSED";
        }
        return "SESSION_NOT_OPEN";
    }

    static ProctorWorkspaceSessionView toWorkspaceSessionView(ExamSessionProctor pa, Instant asOf) {
        ExamSession s = pa.getSession();
        String roleStr = pa.getRole() != null ? pa.getRole().name() : null;
        boolean canMon = canMonitor(s, asOf);
        String reason = monitoringBlockingReason(s, asOf);
        return new ProctorWorkspaceSessionView(
                pa.getPublicId(),
                s.getPublicId(),
                s.getName(),
                s.getSessionCode(),
                s.getStatus(),
                s.getOpensAt(),
                s.getClosesAt(),
                roleStr,
                canMon,
                reason);
    }

    static ProctorMonitoringSessionView toSessionView(ExamSessionProctor pa) {
        ExamSession s = pa.getSession();
        String roleStr = pa.getRole() != null ? pa.getRole().name() : null;
        return new ProctorMonitoringSessionView(
                pa.getPublicId(),
                s.getPublicId(),
                s.getName(),
                s.getSessionCode(),
                s.getStatus(),
                s.getOpensAt(),
                s.getClosesAt(),
                roleStr,
                pa.getProctorPublicId());
    }
}
