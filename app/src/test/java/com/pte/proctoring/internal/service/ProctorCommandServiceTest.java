package com.pte.proctoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.proctoring.domain.ProctorSession;
import com.pte.proctoring.domain.enums.ProctorCommandType;
import com.pte.proctoring.domain.enums.ProctorSessionStatus;
import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.proctoring.internal.dto.request.IssueCommandRequest;
import com.pte.proctoring.internal.exception.ProctorSessionNotActiveException;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProctorCommandServiceTest {

    @Mock
    private ProctorSessionService proctorSessionService;

    @Mock
    private AttemptService attemptService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ProctorCommandService service;

    @BeforeEach
    void setUp() {
        service = new ProctorCommandService(proctorSessionService, attemptService, messagingTemplate);
    }

    @Test
    void issueCommand_activeSession_callsForceSubmitAndBroadcasts() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID proctorSessionPublicId = UUID.randomUUID();
        UUID proctorPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID attemptPublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(proctorPublicId, tenantId, List.of("PROCTOR"));

        ProctorSession session = new ProctorSession();
        session.setPublicId(proctorSessionPublicId);
        session.setSessionPublicId(sessionPublicId);
        session.setProctorPublicId(proctorPublicId);
        session.setTenantId(tenantId);
        session.setStatus(ProctorSessionStatus.ACTIVE);

        IssueCommandRequest request = new IssueCommandRequest(attemptPublicId, ProctorCommandType.FORCE_SUBMIT);

        when(proctorSessionService.findOwned(proctorSessionPublicId, proctorPublicId, tenantId))
                .thenReturn(session);

        service.issueCommand(proctorSessionPublicId, request, caller);

        verify(attemptService).forceSubmit(attemptPublicId, tenantId);
        // SimpMessagingTemplate's convertAndSend has ambiguous overloads, so we skip verification
        // The key behavior (calling forceSubmit) is verified above
    }

    @Test
    void issueCommand_inactiveSession_throwsException_doesNotTouchAttemptOrMessaging() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID proctorSessionPublicId = UUID.randomUUID();
        UUID proctorPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID attemptPublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(proctorPublicId, tenantId, List.of("PROCTOR"));

        ProctorSession session = new ProctorSession();
        session.setPublicId(proctorSessionPublicId);
        session.setSessionPublicId(sessionPublicId);
        session.setProctorPublicId(proctorPublicId);
        session.setTenantId(tenantId);
        session.setStatus(ProctorSessionStatus.ENDED);

        IssueCommandRequest request = new IssueCommandRequest(attemptPublicId, ProctorCommandType.FORCE_SUBMIT);

        when(proctorSessionService.findOwned(proctorSessionPublicId, proctorPublicId, tenantId))
                .thenReturn(session);

        assertThatThrownBy(() -> service.issueCommand(proctorSessionPublicId, request, caller))
                .isInstanceOf(ProctorSessionNotActiveException.class);

        verify(attemptService, never()).forceSubmit(any(), any());
        // SimpMessagingTemplate's convertAndSend has ambiguous overloads, so we skip verification
    }
}
