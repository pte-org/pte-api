package com.pte.practice.internal.service;

import com.pte.attempt.ResponseConfidence;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeSessionItem;
import com.pte.practice.internal.domain.enums.PracticeProgressStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionItemStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.dto.response.PracticeProgressResponse;
import com.pte.practice.internal.repository.PracticeSessionItemRepository;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeProgressServiceTest {

    @Mock
    private PracticeSessionRepository sessionRepository;
    @Mock
    private PracticeSessionItemRepository itemRepository;

    private PracticeProgressService service;
    private UUID studentId;
    private final AtomicLong sessionIds = new AtomicLong(0);

    @BeforeEach
    void setUp() {
        studentId = UUID.randomUUID();
        service = new PracticeProgressService(sessionRepository, itemRepository,
                Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void returnsCompletedHistoryWithoutRecheckingCurrentEntitlement() {
        PracticeSession session = session(PracticeSessionStatus.COMPLETED);
        PracticeSessionItem item = item(PracticeSessionItemStatus.ANSWERED, "answer", ResponseConfidence.HIGH);
        item.setPracticeSessionId(session.getId());
        when(sessionRepository.findTop100ByStudentPublicIdAndDeletedFalseOrderByCreatedAtDesc(studentId))
                .thenReturn(List.of(session));
        when(sessionRepository.countByStudentPublicIdAndDeletedFalse(studentId)).thenReturn(1L);
        when(itemRepository.findByPracticeSessionIdInAndDeletedFalseOrderByPracticeSessionIdAscOrderIndexAsc(
                List.of(session.getId())))
                .thenReturn(List.of(item));

        PracticeProgressResponse response = service.getProgress(studentId);

        assertThat(response.entries()).hasSize(1);
        assertThat(response.entries().getFirst().status()).isEqualTo(PracticeProgressStatus.COMPLETED_PENDING_SCORE);
        assertThat(response.entries().getFirst().highConfidenceCount()).isEqualTo(1);
        assertThat(response.entries().getFirst().score()).isNull();
        assertThat(response.completedSessions()).isEqualTo(1);
        assertThat(response.totalSessions()).isEqualTo(1);
        assertThat(response.hasMore()).isFalse();
    }

    @Test
    void excludesDiscardedAndSkipOnlyHistoryButKeepsNonEmptyExpiredDraft() {
        PracticeSession discarded = session(PracticeSessionStatus.DISCARDED);
        PracticeSession skipped = session(PracticeSessionStatus.COMPLETED);
        PracticeSession expired = session(PracticeSessionStatus.EXPIRED);
        when(sessionRepository.findTop100ByStudentPublicIdAndDeletedFalseOrderByCreatedAtDesc(studentId))
                .thenReturn(List.of(discarded, skipped, expired));
        when(sessionRepository.countByStudentPublicIdAndDeletedFalse(studentId)).thenReturn(3L);
        PracticeSessionItem discardedItem = item(PracticeSessionItemStatus.PENDING, null, null);
        discardedItem.setPracticeSessionId(discarded.getId());
        PracticeSessionItem skippedItem = item(PracticeSessionItemStatus.SKIPPED, null, null);
        skippedItem.setPracticeSessionId(skipped.getId());
        PracticeSessionItem expiredItem = item(PracticeSessionItemStatus.PENDING, "draft", ResponseConfidence.LOW);
        expiredItem.setPracticeSessionId(expired.getId());
        when(itemRepository.findByPracticeSessionIdInAndDeletedFalseOrderByPracticeSessionIdAscOrderIndexAsc(
                List.of(discarded.getId(), skipped.getId(), expired.getId())))
                .thenReturn(List.of(discardedItem, skippedItem, expiredItem));

        PracticeProgressResponse response = service.getProgress(studentId);

        assertThat(response.entries()).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.status()).isEqualTo(PracticeProgressStatus.IN_PROGRESS);
                    assertThat(entry.sessionStatus()).isEqualTo(PracticeSessionStatus.EXPIRED);
                    assertThat(entry.draftItemCount()).isEqualTo(1);
                });
        assertThat(response.hasMore()).isFalse();
    }

    @Test
    void progressEntryCarriesTheSessionKindAndAverageScore() {
        PracticeSession skill = session(PracticeSessionStatus.COMPLETED);
        skill.setKind(com.pte.practice.internal.domain.enums.PracticeSessionKind.SKILL);
        skill.setSkillCode("READING");
        skill.setSelectedTaskTypes("MC_READING_SINGLE,RE_ORDER_PARAGRAPHS");
        PracticeSession legacy = session(PracticeSessionStatus.COMPLETED);
        legacy.setSelectedTaskTypes("READ_ALOUD");
        PracticeSessionItem scored = item(PracticeSessionItemStatus.ANSWERED, "a", null);
        scored.setPracticeSessionId(skill.getId());
        scored.setObjectiveScore(80);
        PracticeSessionItem aiScored = item(PracticeSessionItemStatus.ANSWERED, "b", null);
        aiScored.setPracticeSessionId(skill.getId());
        aiScored.setAiScore(65);
        PracticeSessionItem unscored = item(PracticeSessionItemStatus.ANSWERED, "c", null);
        unscored.setPracticeSessionId(legacy.getId());
        when(sessionRepository.findTop100ByStudentPublicIdAndDeletedFalseOrderByCreatedAtDesc(studentId))
                .thenReturn(List.of(skill, legacy));
        when(sessionRepository.countByStudentPublicIdAndDeletedFalse(studentId)).thenReturn(2L);
        when(itemRepository.findByPracticeSessionIdInAndDeletedFalseOrderByPracticeSessionIdAscOrderIndexAsc(
                List.of(skill.getId(), legacy.getId()))).thenReturn(List.of(scored, aiScored, unscored));

        var entries = service.getProgress(studentId).entries();

        assertThat(entries.get(0).kind()).isEqualTo("SKILL");
        assertThat(entries.get(0).skillCode()).isEqualTo("READING");
        assertThat(entries.get(0).taskCode()).isNull();
        assertThat(entries.get(0).score()).isEqualTo(72.5);
        // A session from before V134 has no kind: it reads as a single-task session.
        assertThat(entries.get(1).kind()).isEqualTo("TASK");
        assertThat(entries.get(1).taskCode()).isEqualTo("READ_ALOUD");
        assertThat(entries.get(1).score()).isNull();
    }

    @Test
    void nullStudentProducesAnEmptyProjection() {
        assertThat(service.getProgress(null).entries()).isEmpty();
    }

    private PracticeSession session(PracticeSessionStatus status) {
        PracticeSession session = new PracticeSession();
        session.setId(sessionIds.incrementAndGet());
        session.setPublicId(UUID.randomUUID());
        session.setStudentPublicId(studentId);
        session.setProductCode("PTE_CORE_PRACTICE");
        session.setTitle("PTE Core Practice");
        session.setStatus(status);
        return session;
    }

    private PracticeSessionItem item(PracticeSessionItemStatus status, String payload,
            ResponseConfidence confidence) {
        PracticeSessionItem item = new PracticeSessionItem();
        item.setStatus(status);
        item.setSavedPayload(payload);
        item.setConfidence(confidence);
        return item;
    }
}
