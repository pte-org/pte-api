package com.pte.practice.internal.service.session;

import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.practice.internal.service.PracticeResponseService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeSessionDiscardServiceTest {

    private final PracticeSessionRepository sessions = mock(PracticeSessionRepository.class);
    private final PracticeResponseService items = mock(PracticeResponseService.class);
    private final CurrentUser caller = mock(CurrentUser.class);
    private final UUID studentId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final PracticeSessionDiscardService service = new PracticeSessionDiscardService(sessions, items,
            Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC));

    private PracticeSession session(PracticeSessionStatus status) {
        PracticeSession session = new PracticeSession();
        session.setStatus(status);
        when(caller.userId()).thenReturn(studentId);
        when(sessions.findWithLockByPublicIdAndStudentPublicId(sessionId, studentId)).thenReturn(Optional.of(session));
        return session;
    }

    @Test
    void dropsABegunSessionWithNothingAnswered() {
        PracticeSession session = session(PracticeSessionStatus.IN_PROGRESS);
        when(items.hasProgress(session)).thenReturn(false);

        assertThat(service.exitWithoutSaving(sessionId, caller)).isTrue();
        assertThat(session.getStatus()).isEqualTo(PracticeSessionStatus.DISCARDED);
        assertThat(session.getDiscardedAt()).isNotNull();
    }

    @Test
    void keepsASessionWithSavedAnswers() {
        PracticeSession session = session(PracticeSessionStatus.IN_PROGRESS);
        when(items.hasProgress(session)).thenReturn(true);

        assertThat(service.exitWithoutSaving(sessionId, caller)).isFalse();
        assertThat(session.getStatus()).isEqualTo(PracticeSessionStatus.IN_PROGRESS);
        verify(sessions, never()).saveAndFlush(any());
    }

    @Test
    void leavesAFinishedSessionAlone() {
        PracticeSession session = session(PracticeSessionStatus.COMPLETED);

        assertThat(service.exitWithoutSaving(sessionId, caller)).isFalse();
        assertThat(session.getStatus()).isEqualTo(PracticeSessionStatus.COMPLETED);
    }
}
