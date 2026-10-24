package com.pte.practice.internal.service;

import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.exception.PracticeSessionNotFoundException;
import com.pte.practice.internal.repository.PracticeSessionItemRepository;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** A review read repairs lost AI jobs before it reports scores, so a stuck item never stays SCORING forever. */
class PracticeReviewServiceRecoveryTest {

    private static final Instant NOW = Instant.parse("2026-10-24T02:00:00Z");

    private final PracticeSessionRepository sessionRepository = mock(PracticeSessionRepository.class);
    private final PracticeSessionItemRepository itemRepository = mock(PracticeSessionItemRepository.class);
    private final PracticeSessionItemService itemService = mock(PracticeSessionItemService.class);
    private final CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("STUDENT"));
    private final UUID sessionId = UUID.randomUUID();
    private PracticeReviewService service;

    @BeforeEach
    void setUp() {
        service = new PracticeReviewService(sessionRepository, itemRepository, JsonMapper.builder().build(),
                itemService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void recoversScoringItemsBeforeReadingThem() {
        PracticeSession session = new PracticeSession();
        session.setId(9L);
        when(sessionRepository.findByPublicIdAndStudentPublicIdAndDeletedFalse(sessionId, caller.userId()))
                .thenReturn(Optional.of(session));
        when(itemRepository.findByPracticeSessionIdAndDeletedFalseOrderByOrderIndexAsc(9L)).thenReturn(List.of());

        service.get(sessionId, caller);

        var order = inOrder(itemService, itemRepository);
        order.verify(itemService).recoverScoring(session, NOW);
        order.verify(itemRepository).findByPracticeSessionIdAndDeletedFalseOrderByOrderIndexAsc(9L);
    }

    @Test
    void anotherStudentsSessionIsNotFound() {
        when(sessionRepository.findByPublicIdAndStudentPublicIdAndDeletedFalse(sessionId, caller.userId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(sessionId, caller)).isInstanceOf(PracticeSessionNotFoundException.class);

        verifyNoInteractions(itemService);
    }
}
