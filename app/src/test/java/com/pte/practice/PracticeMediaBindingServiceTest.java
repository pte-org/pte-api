package com.pte.practice;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeSessionItem;
import com.pte.practice.internal.domain.enums.PracticeSessionItemStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.repository.PracticeSessionItemRepository;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeMediaBindingServiceTest {

    @Mock
    private PracticeSessionRepository sessionRepository;
    @Mock
    private PracticeSessionItemRepository itemRepository;

    private PracticeMediaBindingService service;
    private UUID sessionId;
    private UUID itemId;
    private UUID studentId;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-10-08T00:00:00Z");
        sessionId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        service = new PracticeMediaBindingService(sessionRepository, itemRepository,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void allowsOnlyThePendingRecordingItemInALiveSession() {
        PracticeSession session = session();
        PracticeSessionItem item = item(session);
        when(sessionRepository.findByPublicIdAndStudentPublicIdAndDeletedFalse(sessionId, studentId))
                .thenReturn(Optional.of(session));
        when(itemRepository.findByPublicIdAndPracticeSessionIdAndDeletedFalse(itemId, session.getId()))
                .thenReturn(Optional.of(item));

        assertThatCode(() -> service.assertCanUseResponseAudio(sessionId, itemId,
                new CurrentUser(studentId, null, List.of("STUDENT"))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsExpiredSessionBeforeLookingAtTheItem() {
        PracticeSession session = session();
        session.setDeadlineAt(now.minusSeconds(1));
        when(sessionRepository.findByPublicIdAndStudentPublicIdAndDeletedFalse(sessionId, studentId))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.assertCanUseResponseAudio(sessionId, itemId,
                new CurrentUser(studentId, null, List.of("STUDENT"))))
                .hasFieldOrPropertyWithValue("code", PracticeConstants.PRACTICE_MEDIA_SESSION_NOT_LIVE);
    }

    @Test
    void rejectsANonRecordingOrAlreadyAnsweredItem() {
        PracticeSession session = session();
        PracticeSessionItem item = item(session);
        item.setRendererKey("MC_READING_SINGLE_V1");
        when(sessionRepository.findByPublicIdAndStudentPublicIdAndDeletedFalse(sessionId, studentId))
                .thenReturn(Optional.of(session));
        when(itemRepository.findByPublicIdAndPracticeSessionIdAndDeletedFalse(itemId, session.getId()))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.assertCanUseResponseAudio(sessionId, itemId,
                new CurrentUser(studentId, null, List.of("STUDENT"))))
                .hasFieldOrPropertyWithValue("code", PracticeConstants.PRACTICE_MEDIA_ITEM_NOT_RECORDABLE);

        item.setRendererKey("READ_ALOUD_V1");
        item.setStatus(PracticeSessionItemStatus.ANSWERED);
        assertThatThrownBy(() -> service.assertCanUseResponseAudio(sessionId, itemId,
                new CurrentUser(studentId, null, List.of("STUDENT"))))
                .hasFieldOrPropertyWithValue("code", PracticeConstants.PRACTICE_SESSION_NOT_STARTABLE);
    }

    private PracticeSession session() {
        PracticeSession session = new PracticeSession();
        session.setId(42L);
        session.setPublicId(sessionId);
        session.setStudentPublicId(studentId);
        session.setStatus(PracticeSessionStatus.IN_PROGRESS);
        session.setDeadlineAt(now.plusSeconds(120));
        return session;
    }

    private PracticeSessionItem item(PracticeSession session) {
        PracticeSessionItem item = new PracticeSessionItem();
        item.setPublicId(itemId);
        item.setPracticeSessionId(session.getId());
        item.setRendererKey("READ_ALOUD_V1");
        item.setStatus(PracticeSessionItemStatus.PENDING);
        return item;
    }
}
