package com.pte.attempt.internal.service;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.session.SessionService;
import com.pte.session.internal.exception.NotEntitledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * New: backs the public {@code com.pte.attempt.AttemptService#forceSubmit}
 * that {@code proctoring} (Phase 09) will call directly, in-process. No
 * direct source test existed for {@code ProctorCommandService} — it was only
 * exercised indirectly through {@code ProctorCommandConsumer} (RabbitMQ),
 * which this port doesn't carry (no proctoring caller exists yet).
 */
@ExtendWith(MockitoExtension.class)
class ProctorCommandServiceTest {

    @Mock
    private ExamAttemptRepository attemptRepository;

    @Mock
    private SessionService sessionService;

    private ProctorCommandService service;

    @BeforeEach
    void setUp() {
        service = new ProctorCommandService(attemptRepository, sessionService);
    }

    @Test
    void forceSubmit_inProgressAttempt_submitsAndSaves() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ExamAttempt attempt = new ExamAttempt();
        attempt.setPublicId(attemptPublicId);
        attempt.setTenantId(tenantId);
        attempt.setSessionPublicId(UUID.randomUUID());
        attempt.begin();
        when(attemptRepository.findByPublicIdAndTenantId(attemptPublicId, tenantId)).thenReturn(Optional.of(attempt));

        service.forceSubmit(attemptPublicId, tenantId);

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
        verify(attemptRepository).save(attempt);
    }

    @Test
    void forceSubmit_alreadySubmittedAttempt_isSilentNoOp() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ExamAttempt attempt = new ExamAttempt();
        attempt.setPublicId(attemptPublicId);
        attempt.setTenantId(tenantId);
        attempt.setSessionPublicId(UUID.randomUUID());
        attempt.begin();
        attempt.submit();
        when(attemptRepository.findByPublicIdAndTenantId(attemptPublicId, tenantId)).thenReturn(Optional.of(attempt));

        service.forceSubmit(attemptPublicId, tenantId);

        verify(attemptRepository, never()).save(any());
    }

    @Test
    void forceSubmit_closedSessionRejectsProctorMutation() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ExamAttempt attempt = new ExamAttempt();
        attempt.setPublicId(attemptPublicId);
        attempt.setTenantId(tenantId);
        attempt.setSessionPublicId(sessionId);
        attempt.begin();
        when(attemptRepository.findByPublicIdAndTenantId(attemptPublicId, tenantId)).thenReturn(Optional.of(attempt));
        doThrow(new NotEntitledException()).when(sessionService).lockOpenForAttemptOperation(sessionId, tenantId);

        assertThatThrownBy(() -> service.forceSubmit(attemptPublicId, tenantId))
                .isInstanceOf(NotEntitledException.class);

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void forceSubmit_unknownAttempt_isSilentNoOp() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(attemptRepository.findByPublicIdAndTenantId(attemptPublicId, tenantId)).thenReturn(Optional.empty());

        service.forceSubmit(attemptPublicId, tenantId);

        verify(attemptRepository, never()).save(any());
    }
}
