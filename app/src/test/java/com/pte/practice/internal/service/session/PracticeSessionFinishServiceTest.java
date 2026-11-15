package com.pte.practice.internal.service.session;

import com.pte.practice.internal.domain.PracticeResponse;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.enums.PracticeResponseStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionKind;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.practice.internal.service.PracticeResponseService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeSessionFinishServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T00:00:00Z");

    private final PracticeSessionRepository sessions = mock(PracticeSessionRepository.class);
    private final PracticeResponseService items = mock(PracticeResponseService.class);
    private final CurrentUser caller = mock(CurrentUser.class);
    private final UUID studentId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final PracticeSessionFinishService service = new PracticeSessionFinishService(sessions, items,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private PracticeSession session(PracticeSessionStatus status, PracticeSessionKind kind) {
        PracticeSession session = new PracticeSession();
        session.setStatus(status);
        session.setKind(kind);
        when(caller.userId()).thenReturn(studentId);
        when(sessions.findWithLockByPublicIdAndStudentPublicId(sessionId, studentId)).thenReturn(Optional.of(session));
        return session;
    }

    private static PracticeResponse item(PracticeResponseStatus status) {
        PracticeResponse item = new PracticeResponse();
        item.setStatus(status);
        return item;
    }

    @Test
    void submitsEarlySkippingTheQuestionsNotReached() {
        PracticeSession session = session(PracticeSessionStatus.IN_PROGRESS, PracticeSessionKind.TASK);
        PracticeResponse pending = item(PracticeResponseStatus.PENDING);
        when(items.itemsFor(session)).thenReturn(List.of(item(PracticeResponseStatus.ANSWERED), pending));

        assertThat(service.finish(sessionId, caller)).isTrue();
        verify(items).skip(pending, NOW);
        assertThat(session.getStatus()).isEqualTo(PracticeSessionStatus.COMPLETED);
        assertThat(session.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    void refusesWhenNothingIsAnswered() {
        PracticeSession session = session(PracticeSessionStatus.IN_PROGRESS, PracticeSessionKind.TASK);
        when(items.itemsFor(session)).thenReturn(List.of(item(PracticeResponseStatus.PENDING)));

        assertThat(service.finish(sessionId, caller)).isFalse();
        assertThat(session.getStatus()).isEqualTo(PracticeSessionStatus.IN_PROGRESS);
        verify(sessions, never()).saveAndFlush(any());
    }

    @Test
    void leavesAMockTestToItsOwnFlow() {
        PracticeSession session = session(PracticeSessionStatus.IN_PROGRESS, PracticeSessionKind.MOCK_TEST);
        when(items.itemsFor(session)).thenReturn(List.of(item(PracticeResponseStatus.ANSWERED)));

        assertThat(service.finish(sessionId, caller)).isFalse();
        assertThat(session.getStatus()).isEqualTo(PracticeSessionStatus.IN_PROGRESS);
    }

    @Test
    void repeatingOnACompletedSessionIsHarmless() {
        session(PracticeSessionStatus.COMPLETED, PracticeSessionKind.TASK);

        assertThat(service.finish(sessionId, caller)).isTrue();
        verify(sessions, never()).saveAndFlush(any());
    }
}
