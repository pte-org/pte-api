package com.pte.proctoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.proctoring.internal.dto.response.AttemptActionResultResponse;
import com.pte.proctoring.internal.exception.TargetAttemptInvalidException;
import com.pte.session.SessionService;
import com.pte.session.dto.response.ProctorAssignmentCheckResponse;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProctorCommandDispatchServiceTest {

    @Mock
    private SessionService sessionService;

    @Mock
    private AttemptService attemptService;

    private ProctorCommandDispatchService service;
    private final UUID sessionPublicId = UUID.randomUUID();
    private final UUID attemptPublicId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("PROCTOR"));

    @BeforeEach
    void setUp() {
        service = new ProctorCommandDispatchService(sessionService, attemptService);
        when(sessionService.checkProctorAssignment(sessionPublicId, caller.userId()))
                .thenReturn(new ProctorAssignmentCheckResponse(sessionPublicId, tenantId));
    }

    @Test
    void forceSubmit_inProgressAttemptOfTheSession_isAppliedAfterTheSessionLock() {
        when(attemptService.attemptBelongsToSession(attemptPublicId, sessionPublicId, tenantId)).thenReturn(true);
        when(attemptService.forceSubmitScoped(attemptPublicId, sessionPublicId, tenantId)).thenReturn(true);

        AttemptActionResultResponse result = service.forceSubmit(sessionPublicId, attemptPublicId, caller);

        assertThat(result.attemptPublicId()).isEqualTo(attemptPublicId);
        assertThat(result.outcome()).isEqualTo(ProctorConstants.ATTEMPT_ACTION_APPLIED);
        InOrder order = inOrder(sessionService, attemptService);
        order.verify(sessionService).lockOpenWithinWindowForProctorOperation(sessionPublicId, tenantId);
        order.verify(attemptService).forceSubmitScoped(attemptPublicId, sessionPublicId, tenantId);
    }

    @Test
    void forceSubmit_attemptAlreadySubmitted_reportsNoOp() {
        when(attemptService.attemptBelongsToSession(attemptPublicId, sessionPublicId, tenantId)).thenReturn(true);
        when(attemptService.forceSubmitScoped(attemptPublicId, sessionPublicId, tenantId)).thenReturn(false);

        assertThat(service.forceSubmit(sessionPublicId, attemptPublicId, caller).outcome())
                .isEqualTo(ProctorConstants.ATTEMPT_ACTION_NO_OP);
    }

    @Test
    void forceSubmit_attemptOfAnotherSession_isRejectedWithoutTouchingTheAttempt() {
        when(attemptService.attemptBelongsToSession(attemptPublicId, sessionPublicId, tenantId)).thenReturn(false);

        assertThatThrownBy(() -> service.forceSubmit(sessionPublicId, attemptPublicId, caller))
                .isInstanceOf(TargetAttemptInvalidException.class);

        verify(attemptService, never()).forceSubmitScoped(any(), any(), any());
    }
}
