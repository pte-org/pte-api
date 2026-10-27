package com.pte.practice.internal.service;

import com.pte.practice.internal.service.session.PracticeSessionPersistenceService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PracticeSessionPersistenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    @Mock
    private PracticeSessionRepository sessionRepository;

    @InjectMocks
    private PracticeSessionPersistenceService service;

    @Test
    void replaceStartDiscardsTheUnfinishedAttemptBeforeInsertingItsReplacement() {
        PracticeSession previous = new PracticeSession();
        previous.setStatus(PracticeSessionStatus.IN_PROGRESS);
        PracticeSession replacement = new PracticeSession();
        when(sessionRepository.findById(5L)).thenReturn(Optional.of(previous));
        when(sessionRepository.saveAndFlush(any(PracticeSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PracticeSession saved = service.replaceStart(5L, NOW, replacement);

        assertThat(saved).isSameAs(replacement);
        assertThat(previous.getStatus()).isEqualTo(PracticeSessionStatus.DISCARDED);
        assertThat(previous.getDiscardedAt()).isEqualTo(NOW);
        var order = inOrder(sessionRepository);
        order.verify(sessionRepository).saveAndFlush(previous);
        order.verify(sessionRepository).saveAndFlush(replacement);
    }

    @Test
    void replaceStartLeavesAnAttemptThatAlreadyFinishedAlone() {
        PracticeSession previous = new PracticeSession();
        previous.setStatus(PracticeSessionStatus.COMPLETED);
        PracticeSession replacement = new PracticeSession();
        when(sessionRepository.findById(5L)).thenReturn(Optional.of(previous));
        when(sessionRepository.saveAndFlush(replacement)).thenReturn(replacement);

        service.replaceStart(5L, NOW, replacement);

        assertThat(previous.getStatus()).isEqualTo(PracticeSessionStatus.COMPLETED);
        verify(sessionRepository, never()).saveAndFlush(previous);
    }
}
