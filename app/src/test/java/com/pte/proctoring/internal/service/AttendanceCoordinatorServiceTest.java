package com.pte.proctoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.dto.response.MonitoringAttemptView;
import com.pte.identity.IdentityService;
import com.pte.proctoring.internal.exception.AbsentAfterAttemptException;
import com.pte.session.SessionService;
import com.pte.session.domain.enums.AttendanceStatus;
import com.pte.session.dto.request.RecordAttendanceRequest;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttendanceCoordinatorServiceTest {

    private final SessionService sessionService = mock(SessionService.class);
    private final AttemptService attemptService = mock(AttemptService.class);
    private final AttendanceCoordinatorService service =
            new AttendanceCoordinatorService(sessionService, attemptService, mock(IdentityService.class));

    private final UUID sessionId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final CurrentUser caller = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("PROCTOR"));

    private RecordAttendanceRequest request(AttendanceStatus status) {
        return new RecordAttendanceRequest(status, UUID.randomUUID(), 1L, UUID.randomUUID(), "reason");
    }

    private void latestAttempt(AttemptStatus status) {
        MonitoringAttemptView view = new MonitoringAttemptView(
                UUID.randomUUID(), studentId, 1, status, null, null, null, 1);
        when(attemptService.getLatestAttemptsForStudents(any(), any(), any())).thenReturn(Map.of(studentId, view));
    }

    @Test
    void absentIsRejectedOnceTheStudentStartedTheExam() {
        for (AttemptStatus status : List.of(AttemptStatus.IN_PROGRESS, AttemptStatus.SUBMITTED)) {
            latestAttempt(status);
            assertThrows(AbsentAfterAttemptException.class,
                    () -> service.recordAttendance(sessionId, studentId, request(AttendanceStatus.ABSENT), caller, false));
        }
        verify(sessionService, never()).recordAttendance(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void absentIsAllowedWhenTheStudentNeverStarted() {
        when(attemptService.getLatestAttemptsForStudents(any(), any(), any())).thenReturn(Map.of());
        service.recordAttendance(sessionId, studentId, request(AttendanceStatus.ABSENT), caller, false);
        verify(sessionService).recordAttendance(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void otherStatusesAreNotAffectedByAnAttempt() {
        latestAttempt(AttemptStatus.SUBMITTED);
        service.recordAttendance(sessionId, studentId, request(AttendanceStatus.PRESENT), caller, false);
        verify(sessionService).recordAttendance(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }
}
