package com.pte.proctoring.internal.service;

import com.pte.proctoring.internal.dto.response.ProctorSessionResponse;
import com.pte.session.SessionService;
import com.pte.session.dto.response.ProctorAssignmentCheckResponse;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProctorWatchServiceTest {

    @Mock
    private SessionService sessionService;

    private ProctorWatchService service;
    private final UUID sessionPublicId = UUID.randomUUID();
    private final UUID proctorPublicId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final CurrentUser caller = new CurrentUser(proctorPublicId, tenantId, List.of("PROCTOR"));

    @BeforeEach
    void setUp() {
        service = new ProctorWatchService(sessionService);
    }

    @Test
    void open_assignedProctor_returnsAnActiveWatchWhoseIdIsTheExamSessionId() {
        when(sessionService.checkProctorAssignment(sessionPublicId, proctorPublicId))
                .thenReturn(new ProctorAssignmentCheckResponse(sessionPublicId, tenantId));

        ProctorSessionResponse result = service.open(sessionPublicId, caller);

        assertThat(result.publicId()).isEqualTo(sessionPublicId);
        assertThat(result.sessionPublicId()).isEqualTo(sessionPublicId);
        assertThat(result.proctorPublicId()).isEqualTo(proctorPublicId);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.openedAt()).isNotNull();
        verify(sessionService).lockOpenWithinWindowForProctorOperation(sessionPublicId, tenantId);
        verify(sessionService).checkProctorAssignment(sessionPublicId, proctorPublicId);
    }

    @Test
    void open_checkProctorAssignmentThrows_propagatesException() {
        when(sessionService.checkProctorAssignment(sessionPublicId, proctorPublicId))
                .thenThrow(new RuntimeException("Proctor not assigned"));

        assertThatThrownBy(() -> service.open(sessionPublicId, caller))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Proctor not assigned");

        verify(sessionService).lockOpenWithinWindowForProctorOperation(sessionPublicId, tenantId);
    }

    @Test
    void open_sessionOutsideWindowOrClosed_throwsBeforeCheckingAssignment() {
        doThrow(new RuntimeException("Session closed"))
                .when(sessionService).lockOpenWithinWindowForProctorOperation(sessionPublicId, tenantId);

        assertThatThrownBy(() -> service.open(sessionPublicId, caller))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Session closed");

        verify(sessionService, never()).checkProctorAssignment(any(), any());
    }

    @Test
    void authorizeCommand_returnsTheAssignmentTenantAfterLockingTheOpenSession() {
        UUID assignmentTenant = UUID.randomUUID();
        when(sessionService.checkProctorAssignment(sessionPublicId, proctorPublicId))
                .thenReturn(new ProctorAssignmentCheckResponse(sessionPublicId, assignmentTenant));

        assertThat(service.authorizeCommand(sessionPublicId, caller)).isEqualTo(assignmentTenant);

        verify(sessionService).lockOpenWithinWindowForProctorOperation(sessionPublicId, tenantId);
    }
}
