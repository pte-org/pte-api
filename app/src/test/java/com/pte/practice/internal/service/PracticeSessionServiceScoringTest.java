package com.pte.practice.internal.service;

import com.pte.identity.PracticeIdentityService;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeSessionItem;
import com.pte.practice.internal.dto.response.PracticeItemResultResponse;
import com.pte.practice.internal.exception.PracticeSessionNotFoundException;
import com.pte.practice.internal.mapper.PracticeSessionResponseMapper;
import com.pte.practice.internal.repository.PracticeSessionOperationRepository;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Poll and retry of one item's AI score: ownership first, recovery before every read, retry under a lock. */
class PracticeSessionServiceScoringTest {

    private static final Instant NOW = Instant.parse("2026-10-24T02:00:00Z");

    private final PracticeSessionRepository sessionRepository = mock(PracticeSessionRepository.class);
    private final PracticeSessionItemService itemService = mock(PracticeSessionItemService.class);
    private final PracticeEntitlementService entitlementService = mock(PracticeEntitlementService.class);
    private final CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("STUDENT"));
    private final UUID sessionId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final PracticeItemResultResponse result = mock(PracticeItemResultResponse.class);

    private PracticeSessionService service;
    private PracticeSession session;
    private PracticeSessionItem item;

    @BeforeEach
    void setUp() {
        service = new PracticeSessionService(sessionRepository, null, mock(PracticeSessionOperationRepository.class),
                null, entitlementService, itemService, mock(PracticeSessionResponseMapper.class),
                mock(PracticeIdentityService.class), Clock.fixed(NOW, ZoneOffset.UTC));
        session = new PracticeSession();
        session.setId(42L);
        session.setTenantId(UUID.randomUUID());
        item = new PracticeSessionItem();
        when(itemService.resultOf(item)).thenReturn(result);
    }

    @Test
    void pollRecoversThenReturnsTheItemResult() {
        when(sessionRepository.findByPublicIdAndStudentPublicIdAndDeletedFalse(sessionId, caller.userId()))
                .thenReturn(Optional.of(session));
        when(itemService.findForUpdate(session, itemId)).thenReturn(item);

        assertThat(service.itemResult(sessionId, itemId, caller)).isSameAs(result);

        var order = inOrder(itemService);
        order.verify(itemService).recoverScoring(session, item, NOW);
        order.verify(itemService).resultOf(item);
    }

    @Test
    void pollOfAnotherStudentsSessionIsNotFoundAndTouchesNoItem() {
        when(sessionRepository.findByPublicIdAndStudentPublicIdAndDeletedFalse(sessionId, caller.userId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.itemResult(sessionId, itemId, caller))
                .isInstanceOf(PracticeSessionNotFoundException.class);

        verifyNoInteractions(itemService);
    }

    @Test
    void pollOfAnItemOutsideTheSessionIsNotFound() {
        when(sessionRepository.findByPublicIdAndStudentPublicIdAndDeletedFalse(sessionId, caller.userId()))
                .thenReturn(Optional.of(session));
        when(itemService.findForUpdate(session, itemId)).thenThrow(new PracticeSessionNotFoundException());

        assertThatThrownBy(() -> service.itemResult(sessionId, itemId, caller))
                .isInstanceOf(PracticeSessionNotFoundException.class);

        verify(itemService, never()).resultOf(any());
    }

    @Test
    void retryChecksEntitlementThenRetriesTheLockedItem() {
        when(sessionRepository.findWithLockByPublicIdAndStudentPublicId(sessionId, caller.userId()))
                .thenReturn(Optional.of(session));
        when(itemService.findForUpdate(session, itemId)).thenReturn(item);
        when(itemService.retryScoring(session, item, NOW)).thenReturn(result);

        assertThat(service.retryScoring(sessionId, itemId, caller)).isSameAs(result);

        var order = inOrder(entitlementService, itemService);
        order.verify(entitlementService).assertUnlocked(caller, session.getTenantId());
        order.verify(itemService).retryScoring(session, item, NOW);
    }

    @Test
    void retryOfAnotherStudentsSessionIsNotFound() {
        when(sessionRepository.findWithLockByPublicIdAndStudentPublicId(sessionId, caller.userId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retryScoring(sessionId, itemId, caller))
                .isInstanceOf(PracticeSessionNotFoundException.class);

        verifyNoInteractions(itemService, entitlementService);
    }
}
