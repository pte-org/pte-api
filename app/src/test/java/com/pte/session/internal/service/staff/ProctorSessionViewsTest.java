package com.pte.session.internal.service.staff;

import com.pte.session.domain.ExamSession;
import com.pte.session.domain.ExamSessionProctor;
import com.pte.session.domain.enums.ProctorRole;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.dto.response.ProctorMonitoringSessionView;
import com.pte.session.dto.response.ProctorWorkspaceSessionView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** When a proctor can monitor a session, why not, and how an assignment becomes a view. */
class ProctorSessionViewsTest {

    private static final Instant OPENS = Instant.parse("2026-11-03T08:00:00Z");
    private static final Instant CLOSES = Instant.parse("2026-11-03T10:00:00Z");

    private ExamSession session(SessionStatus status, Instant opensAt, Instant closesAt) {
        ExamSession session = new ExamSession();
        session.setPublicId(UUID.randomUUID());
        session.setName("Mock A");
        session.setSessionCode("MA-01");
        session.setStatus(status);
        session.setOpensAt(opensAt);
        session.setClosesAt(closesAt);
        return session;
    }

    private ExamSessionProctor assignment(ExamSession session, ProctorRole role) {
        ExamSessionProctor assignment = new ExamSessionProctor();
        assignment.setPublicId(UUID.randomUUID());
        assignment.setSession(session);
        assignment.setProctorPublicId(UUID.randomUUID());
        assignment.setRole(role);
        return assignment;
    }

    @Test
    void canMonitor_openSessionInsideItsWindow_isTrue() {
        assertThat(ProctorSessionViews.canMonitor(session(SessionStatus.OPEN, OPENS, CLOSES), OPENS.plusSeconds(60)))
                .isTrue();
    }

    @Test
    void canMonitor_exactlyAtOpeningInstant_isTrue() {
        assertThat(ProctorSessionViews.canMonitor(session(SessionStatus.OPEN, OPENS, CLOSES), OPENS)).isTrue();
    }

    @Test
    void canMonitor_exactlyAtClosingInstant_isFalse() {
        assertThat(ProctorSessionViews.canMonitor(session(SessionStatus.OPEN, OPENS, CLOSES), CLOSES)).isFalse();
    }

    @Test
    void canMonitor_oneSecondBeforeClosing_isTrue() {
        assertThat(ProctorSessionViews.canMonitor(session(SessionStatus.OPEN, OPENS, CLOSES), CLOSES.minusSeconds(1)))
                .isTrue();
    }

    @Test
    void canMonitor_beforeTheWindow_isFalse() {
        assertThat(ProctorSessionViews.canMonitor(session(SessionStatus.OPEN, OPENS, CLOSES), OPENS.minusSeconds(1)))
                .isFalse();
    }

    @Test
    void canMonitor_nullSession_isFalse() {
        assertThat(ProctorSessionViews.canMonitor(null, OPENS)).isFalse();
    }

    @Test
    void canMonitor_deletedSession_isFalse() {
        ExamSession deleted = session(SessionStatus.OPEN, OPENS, CLOSES);
        deleted.setDeleted(true);

        assertThat(ProctorSessionViews.canMonitor(deleted, OPENS.plusSeconds(1))).isFalse();
    }

    @Test
    void canMonitor_sessionNotOpen_isFalseEvenInsideItsWindow() {
        for (SessionStatus status : new SessionStatus[] {SessionStatus.DRAFT, SessionStatus.READY,
                SessionStatus.SCHEDULED, SessionStatus.CLOSED, SessionStatus.CANCELLED}) {
            assertThat(ProctorSessionViews.canMonitor(session(status, OPENS, CLOSES), OPENS.plusSeconds(1)))
                    .as(status.name()).isFalse();
        }
    }

    @Test
    void canMonitor_missingWindowBound_isFalse() {
        assertThat(ProctorSessionViews.canMonitor(session(SessionStatus.OPEN, null, CLOSES), OPENS)).isFalse();
        assertThat(ProctorSessionViews.canMonitor(session(SessionStatus.OPEN, OPENS, null), OPENS)).isFalse();
    }

    @Test
    void blockingReason_monitorableSession_isNull() {
        assertThat(ProctorSessionViews.monitoringBlockingReason(
                session(SessionStatus.OPEN, OPENS, CLOSES), OPENS.plusSeconds(1))).isNull();
    }

    @Test
    void blockingReason_nullSession_isSessionNotFound() {
        assertThat(ProctorSessionViews.monitoringBlockingReason(null, OPENS)).isEqualTo("SESSION_NOT_FOUND");
    }

    @Test
    void blockingReason_cancelledAndClosedSessions_nameTheirState() {
        assertThat(ProctorSessionViews.monitoringBlockingReason(session(SessionStatus.CANCELLED, OPENS, CLOSES), OPENS))
                .isEqualTo("SESSION_CANCELLED");
        assertThat(ProctorSessionViews.monitoringBlockingReason(session(SessionStatus.CLOSED, OPENS, CLOSES), OPENS))
                .isEqualTo("SESSION_CLOSED");
    }

    @Test
    void blockingReason_draftAndReadySessions_areNotOpen() {
        assertThat(ProctorSessionViews.monitoringBlockingReason(session(SessionStatus.DRAFT, OPENS, CLOSES), OPENS))
                .isEqualTo("SESSION_NOT_OPEN");
        assertThat(ProctorSessionViews.monitoringBlockingReason(session(SessionStatus.READY, OPENS, CLOSES), OPENS))
                .isEqualTo("SESSION_NOT_OPEN");
    }

    @Test
    void blockingReason_scheduledSessionWithoutWindow_isWindowNotConfigured() {
        assertThat(ProctorSessionViews.monitoringBlockingReason(session(SessionStatus.SCHEDULED, null, null), OPENS))
                .isEqualTo("WINDOW_NOT_CONFIGURED");
    }

    @Test
    void blockingReason_scheduledSessionBeforeOpening_isWindowNotOpened() {
        assertThat(ProctorSessionViews.monitoringBlockingReason(
                session(SessionStatus.SCHEDULED, OPENS, CLOSES), OPENS.minusSeconds(1))).isEqualTo("WINDOW_NOT_OPENED");
    }

    @Test
    void blockingReason_openSessionAfterTheWindow_isWindowClosed() {
        assertThat(ProctorSessionViews.monitoringBlockingReason(session(SessionStatus.OPEN, OPENS, CLOSES), CLOSES))
                .isEqualTo("WINDOW_CLOSED");
    }

    @Test
    void blockingReason_scheduledSessionInsideTheWindow_isStillNotOpen() {
        assertThat(ProctorSessionViews.monitoringBlockingReason(
                session(SessionStatus.SCHEDULED, OPENS, CLOSES), OPENS.plusSeconds(1))).isEqualTo("SESSION_NOT_OPEN");
    }

    @Test
    void toWorkspaceSessionView_carriesSessionFieldsRoleAndMonitorability() {
        ExamSession open = session(SessionStatus.OPEN, OPENS, CLOSES);
        ExamSessionProctor assignment = assignment(open, ProctorRole.LEAD_PROCTOR);

        ProctorWorkspaceSessionView view = ProctorSessionViews.toWorkspaceSessionView(assignment, OPENS.plusSeconds(1));

        assertThat(view.assignmentPublicId()).isEqualTo(assignment.getPublicId());
        assertThat(view.examSessionPublicId()).isEqualTo(open.getPublicId());
        assertThat(view.name()).isEqualTo("Mock A");
        assertThat(view.sessionCode()).isEqualTo("MA-01");
        assertThat(view.assignmentRole()).isEqualTo("LEAD_PROCTOR");
        assertThat(view.canMonitor()).isTrue();
        assertThat(view.monitoringBlockingReason()).isNull();
    }

    @Test
    void toWorkspaceSessionView_afterTheWindow_reportsWhyMonitoringIsBlocked() {
        ExamSessionProctor assignment = assignment(session(SessionStatus.OPEN, OPENS, CLOSES), ProctorRole.ASSISTANT_PROCTOR);

        ProctorWorkspaceSessionView view = ProctorSessionViews.toWorkspaceSessionView(assignment, CLOSES.plusSeconds(1));

        assertThat(view.canMonitor()).isFalse();
        assertThat(view.monitoringBlockingReason()).isEqualTo("WINDOW_CLOSED");
    }

    @Test
    void toWorkspaceSessionView_assignmentWithoutRole_hasNullRole() {
        ExamSessionProctor assignment = assignment(session(SessionStatus.OPEN, OPENS, CLOSES), null);

        assertThat(ProctorSessionViews.toWorkspaceSessionView(assignment, OPENS).assignmentRole()).isNull();
    }

    @Test
    void toSessionView_exposesTheProctorWhoHoldsTheAssignment() {
        ExamSessionProctor assignment = assignment(session(SessionStatus.OPEN, OPENS, CLOSES), ProctorRole.ASSISTANT_PROCTOR);

        ProctorMonitoringSessionView view = ProctorSessionViews.toSessionView(assignment);

        assertThat(view.assignmentRole()).isEqualTo("ASSISTANT_PROCTOR");
        assertThat(view.proctorPublicId()).isEqualTo(assignment.getProctorPublicId());
        assertThat(view.status()).isEqualTo(SessionStatus.OPEN);
    }
}
