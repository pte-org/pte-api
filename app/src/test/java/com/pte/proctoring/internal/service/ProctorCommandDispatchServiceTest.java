package com.pte.proctoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.proctoring.domain.enums.ProctorCommandType;
import com.pte.proctoring.internal.dto.request.IssueCommandRequest;
import com.pte.proctoring.internal.exception.TargetAttemptInvalidException;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProctorCommandDispatchServiceTest {

    @Mock
    private ProctorWatchService watchService;

    @Mock
    private AttemptService attemptService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ProctorCommandDispatchService service;
    private final UUID sessionPublicId = UUID.randomUUID();
    private final UUID attemptPublicId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("PROCTOR"));
    private final IssueCommandRequest request =
            new IssueCommandRequest(attemptPublicId, ProctorCommandType.FORCE_SUBMIT);

    @BeforeEach
    void setUp() {
        service = new ProctorCommandDispatchService(watchService, attemptService, messagingTemplate);
        when(watchService.authorizeCommand(sessionPublicId, caller)).thenReturn(tenantId);
    }

    @Test
    void issueCommand_attemptOfTheSession_forceSubmitsAndReportsTheOutcome() {
        when(attemptService.attemptBelongsToSession(attemptPublicId, sessionPublicId, tenantId)).thenReturn(true);
        when(attemptService.forceSubmitScoped(attemptPublicId, sessionPublicId, tenantId)).thenReturn(true);

        assertThat(service.issueCommand(sessionPublicId, request, caller)).isTrue();

        verify(attemptService).forceSubmitScoped(attemptPublicId, sessionPublicId, tenantId);
    }

    @Test
    void issueCommand_attemptOfAnotherSession_isRejectedWithoutTouchingTheAttempt() {
        when(attemptService.attemptBelongsToSession(attemptPublicId, sessionPublicId, tenantId)).thenReturn(false);

        assertThatThrownBy(() -> service.issueCommand(sessionPublicId, request, caller))
                .isInstanceOf(TargetAttemptInvalidException.class);

        verify(attemptService, never()).forceSubmitScoped(any(), any(), any());
    }
}
