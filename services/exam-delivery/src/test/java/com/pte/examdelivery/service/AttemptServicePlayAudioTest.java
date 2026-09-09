package com.pte.examdelivery.service;

import com.pte.common.security.CurrentUser;
import com.pte.examdelivery.config.EncryptionKeyProvider;
import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.domain.PinnedExamSnapshot;
import com.pte.examdelivery.domain.PinnedItem;
import com.pte.examdelivery.domain.exception.AttemptAlreadyCompleteException;
import com.pte.examdelivery.domain.exception.AudioUrlExpiredException;
import com.pte.examdelivery.domain.exception.NotCurrentTaskException;
import com.pte.examdelivery.domain.exception.ReplayLimitExceededException;
import com.pte.examdelivery.dto.response.AudioPlayResponse;
import com.pte.examdelivery.mapper.AttemptMapper;
import com.pte.examdelivery.messaging.outbox.OutboxWriter;
import com.pte.examdelivery.repository.ExamAttemptRepository;
import com.pte.examdelivery.repository.PinnedItemRepository;
import com.pte.examdelivery.service.cache.PinnedSnapshotCacheService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers {@code AttemptService.playAudio} after client-side-exam-timer Phase 1
 * relocated {@code playCount}/{@code lastPlayRequestId}/{@code lastPlayAllowed}
 * off {@code TimerState} onto {@code ExamAttempt} — no dedicated test existed
 * for this method before this phase (a pre-existing gap, not introduced here).
 * Confirms the pessimistic lock now targets {@code ExamAttemptRepository.findWithLockById}
 * (not {@code TimerState}), and that {@code playAudio} no longer touches
 * {@code TimerService} at all (it never needed deadline data to begin with).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttemptService.playAudio (client-side-exam-timer Phase 1)")
class AttemptServicePlayAudioTest {

    @Mock
    private ExamAttemptRepository attemptRepository;

    @Mock
    private PinnedItemRepository pinnedItemRepository;

    @Mock
    private SnapshotPinService snapshotPinService;

    @Mock
    private PinnedSnapshotCacheService cacheService;

    @Mock
    private TimerService timerService;

    @Mock
    private AnswerSubmitService answerSubmitService;

    @Mock
    private OutboxWriter outboxWriter;

    @Mock
    private EncryptionKeyProvider encryptionKeyProvider;

    @Mock
    private SubmissionDecryptionService submissionDecryptionService;

    @Mock
    private HeartbeatService heartbeatService;

    private AttemptService attemptService;

    private UUID studentPublicId;
    private CurrentUser caller;
    private UUID attemptPublicId;
    private UUID pinnedItemPublicId;
    private ExamAttempt attempt;
    private PinnedItem item;

    @BeforeEach
    void setUp() {
        AttemptMapper attemptMapper = new AttemptMapper(JsonMapper.builder().build());
        attemptService = new AttemptService(
            attemptRepository,
            pinnedItemRepository,
            snapshotPinService,
            cacheService,
            timerService,
            answerSubmitService,
            attemptMapper,
            outboxWriter,
            encryptionKeyProvider,
            submissionDecryptionService,
            heartbeatService
        );

        studentPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        caller = new CurrentUser(studentPublicId, tenantId, List.of("student"));
        attemptPublicId = UUID.randomUUID();
        pinnedItemPublicId = UUID.randomUUID();

        attempt = new ExamAttempt();
        attempt.setId(1L);
        attempt.setPublicId(attemptPublicId);
        attempt.setStudentPublicId(studentPublicId);
        attempt.setTenantId(tenantId);
        attempt.begin();
        attempt.setCurrentOrderIndex(0);

        PinnedExamSnapshot snapshot = new PinnedExamSnapshot();
        snapshot.setId(1L);
        snapshot.setReplayPolicyType("LIMITED");
        snapshot.setReplayPolicyLimit(2);
        attempt.setPinnedSnapshot(snapshot);

        item = new PinnedItem();
        item.setId(1L);
        item.setPublicId(pinnedItemPublicId);
        item.setOrderIndex(0);
        item.setSection("LISTENING");
        item.setTaskType("SUMMARIZE_SPOKEN_TEXT");
        item.setTitle("Test task");
        item.setAudioUrl("https://minio.local/signed-audio");
        item.setAudioUrlExpiresAt(Instant.now().plusSeconds(600));
        item.setPinnedSnapshot(snapshot);

        when(attemptRepository.findWithPinnedByPublicIdAndStudentPublicId(attemptPublicId, studentPublicId))
            .thenReturn(Optional.of(attempt));
        // lenient: playAudio_attemptAlreadyComplete_throwsWithoutLocking short-circuits
        // on attempt.getStatus() before ever reaching the pinned-item lookup.
        org.mockito.Mockito.lenient().when(pinnedItemRepository.findByPinnedSnapshotIdAndOrderIndex(snapshot.getId(), 0))
            .thenReturn(Optional.of(item));
    }

    @Test
    @DisplayName("locks the ExamAttempt row (relocated from TimerState) and never touches TimerService")
    void playAudio_locksExamAttemptRow_neverTouchesTimerService() {
        when(attemptRepository.findWithLockById(1L)).thenReturn(Optional.of(attempt));

        AudioPlayResponse response = attemptService.playAudio(attemptPublicId, pinnedItemPublicId, "req-1", caller);

        assertThat(response.audioUrl()).isEqualTo("https://minio.local/signed-audio");
        verify(attemptRepository).findWithLockById(1L);
        verifyNoInteractions(timerService);
    }

    @Test
    @DisplayName("first play increments playCount and persists the attempt")
    void playAudio_firstPlay_incrementsPlayCount() {
        when(attemptRepository.findWithLockById(1L)).thenReturn(Optional.of(attempt));
        when(attemptRepository.save(any(ExamAttempt.class))).thenReturn(attempt);

        attemptService.playAudio(attemptPublicId, pinnedItemPublicId, "req-1", caller);

        assertThat(attempt.getPlayCount()).isEqualTo(1);
        assertThat(attempt.getLastPlayRequestId()).isEqualTo("req-1");
        assertThat(attempt.getLastPlayAllowed()).isTrue();
        verify(attemptRepository).save(attempt);
    }

    @Test
    @DisplayName("repeated request with the same playRequestId replays the prior outcome without incrementing again")
    void playAudio_sameRequestIdReplayed_doesNotIncrementAgain() {
        attempt.setPlayCount(1);
        attempt.setLastPlayRequestId("req-1");
        attempt.setLastPlayAllowed(true);
        when(attemptRepository.findWithLockById(1L)).thenReturn(Optional.of(attempt));

        AudioPlayResponse response = attemptService.playAudio(attemptPublicId, pinnedItemPublicId, "req-1", caller);

        assertThat(response.audioUrl()).isEqualTo("https://minio.local/signed-audio");
        assertThat(attempt.getPlayCount()).isEqualTo(1);
        verify(attemptRepository, never()).save(any());
    }

    @Test
    @DisplayName("replay limit exceeded throws and persists the rejected attempt (allowed=false)")
    void playAudio_replayLimitExceeded_throws() {
        attempt.setPlayCount(2); // policy limit is 2
        when(attemptRepository.findWithLockById(1L)).thenReturn(Optional.of(attempt));
        when(attemptRepository.save(any(ExamAttempt.class))).thenReturn(attempt);

        assertThatThrownBy(() -> attemptService.playAudio(attemptPublicId, pinnedItemPublicId, "req-2", caller))
            .isInstanceOf(ReplayLimitExceededException.class);

        assertThat(attempt.getLastPlayAllowed()).isFalse();
        verify(attemptRepository).save(attempt);
    }

    @Test
    @DisplayName("expired audio URL throws before any replay-count logic runs")
    void playAudio_audioUrlExpired_throws() {
        item.setAudioUrlExpiresAt(Instant.now().minusSeconds(1));
        when(attemptRepository.findWithLockById(1L)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> attemptService.playAudio(attemptPublicId, pinnedItemPublicId, "req-1", caller))
            .isInstanceOf(AudioUrlExpiredException.class);
    }

    @Test
    @DisplayName("a pinnedItemPublicId that isn't the current item throws NotCurrentTaskException")
    void playAudio_notCurrentItem_throws() {
        when(attemptRepository.findWithLockById(1L)).thenReturn(Optional.of(attempt));
        UUID staleItemPublicId = UUID.randomUUID();

        assertThatThrownBy(() -> attemptService.playAudio(attemptPublicId, staleItemPublicId, "req-1", caller))
            .isInstanceOf(NotCurrentTaskException.class);
    }

    @Test
    @DisplayName("an already-completed attempt throws before acquiring the lock")
    void playAudio_attemptAlreadyComplete_throwsWithoutLocking() {
        attempt.submit();

        assertThatThrownBy(() -> attemptService.playAudio(attemptPublicId, pinnedItemPublicId, "req-1", caller))
            .isInstanceOf(AttemptAlreadyCompleteException.class);

        verify(attemptRepository, never()).findWithLockById(any());
    }

    @Test
    @DisplayName("UNLIMITED replay policy (limit sentinel < 0) never rejects regardless of playCount")
    void playAudio_unlimitedPolicy_neverRejects() {
        attempt.getPinnedSnapshot().setReplayPolicyType("UNLIMITED");
        attempt.setPlayCount(50);
        when(attemptRepository.findWithLockById(1L)).thenReturn(Optional.of(attempt));
        when(attemptRepository.save(any(ExamAttempt.class))).thenReturn(attempt);

        AudioPlayResponse response = attemptService.playAudio(attemptPublicId, pinnedItemPublicId, "req-51", caller);

        assertThat(response.audioUrl()).isEqualTo("https://minio.local/signed-audio");
        assertThat(attempt.getPlayCount()).isEqualTo(51);
    }
}
