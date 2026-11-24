package com.pte.attempt.internal.listener;

import com.pte.TestClocks;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.session.dto.event.SessionClosedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionClosedAttemptSubmitterTest {

    private final UUID sessionId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    @Mock
    private ExamAttemptRepository attempts;

    private SessionClosedAttemptSubmitter submitter() {
        return new SessionClosedAttemptSubmitter(attempts, TestClocks.fixed());
    }

    private ExamAttempt attempt(boolean deleted) {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setSessionPublicId(sessionId);
        attempt.setTenantId(tenantId);
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        attempt.setDeleted(deleted);
        return attempt;
    }

    @Test
    void submitsEveryAttemptStillInProgressWhenTheSessionCloses() {
        ExamAttempt first = attempt(false);
        ExamAttempt second = attempt(false);
        when(attempts.findBySessionPublicIdAndTenantIdAndStatus(sessionId, tenantId, AttemptStatus.IN_PROGRESS))
                .thenReturn(List.of(first, second));

        submitter().onSessionClosed(new SessionClosedEvent(sessionId, tenantId));

        for (ExamAttempt attempt : List.of(first, second)) {
            assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
            assertThat(attempt.getSubmittedAt()).isEqualTo(TestClocks.fixed().instant());
        }
        verify(attempts).saveAll(List.of(first, second));
    }

    @Test
    void leavesDeletedAttemptsAlone() {
        ExamAttempt deleted = attempt(true);
        when(attempts.findBySessionPublicIdAndTenantIdAndStatus(sessionId, tenantId, AttemptStatus.IN_PROGRESS))
                .thenReturn(List.of(deleted));

        submitter().onSessionClosed(new SessionClosedEvent(sessionId, tenantId));

        assertThat(deleted.getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
        verify(attempts, never()).saveAll(anyList());
    }

    @Test
    void doesNothingWhenNobodyIsStillWorking() {
        when(attempts.findBySessionPublicIdAndTenantIdAndStatus(sessionId, tenantId, AttemptStatus.IN_PROGRESS))
                .thenReturn(List.of());

        submitter().onSessionClosed(new SessionClosedEvent(sessionId, tenantId));

        verify(attempts, never()).saveAll(anyList());
    }
}
