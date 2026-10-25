package com.pte.attempt.internal.service;

import com.pte.attempt.internal.config.EncryptionKeyProvider;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.internal.exception.AttemptAlreadyCompleteException;
import com.pte.attempt.internal.exception.AudioUrlExpiredException;
import com.pte.attempt.internal.exception.NotCurrentTaskException;
import com.pte.attempt.internal.exception.ReplayLimitExceededException;
import com.pte.attempt.internal.dto.response.AudioPlayResponse;
import com.pte.attempt.internal.mapper.AttemptMapper;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.attempt.internal.service.cache.ExamItemView;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;

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
 * Ported from services/exam-delivery's own AttemptServicePlayAudioTest —
 * same coverage, new package. Confirms the pessimistic lock targets {@code
 * ExamAttemptRepository.findWithLockById}, and that {@code playAudio} never
 * touches {@code TimerService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttemptLifecycleService.playAudio")
class AttemptLifecycleServicePlayAudioTest {

    @Mock
    private ExamAttemptRepository attemptRepository;

    private final FakeExam exam = new FakeExam();

    @Mock
    private TimerService timerService;

    @Mock
    private AnswerSubmitService answerSubmitService;

    @Mock
    private EncryptionKeyProvider encryptionKeyProvider;

    @Mock
    private SubmissionDecryptionService submissionDecryptionService;

    @Mock
    private HeartbeatService heartbeatService;
    @Mock
    private CapabilityNegotiationService capabilityNegotiationService;
    @Mock
    private SessionService sessionService;

    private AttemptLifecycleService attemptLifecycleService;

    private UUID studentPublicId;
    private CurrentUser caller;
    private UUID attemptPublicId;
    private UUID pinnedItemPublicId;
    private ExamAttempt attempt;
    private ExamItemView item;

    @BeforeEach
    void setUp() {
        AttemptMapper attemptMapper = new AttemptMapper(JsonMapper.builder().build());
        attemptLifecycleService = new AttemptLifecycleService(
                attemptRepository,
                exam.contentService,
                timerService,
                answerSubmitService,
                attemptMapper,
                encryptionKeyProvider,
                submissionDecryptionService,
                heartbeatService,
                capabilityNegotiationService,
                sessionService);

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

        attempt.setReplayPolicyType("LIMITED");
        attempt.setReplayPolicyLimit(2);

        item = FakeExam.withAudio(FakeExam.withId(FakeExam.view(0, "LISTENING", "SUMMARIZE_SPOKEN_TEXT", 0, 60),
                pinnedItemPublicId), "https://res.cloudinary.com/test/signed-audio", Instant.now().plusSeconds(600));
        exam.add(item);

        when(attemptRepository.findByPublicIdAndStudentPublicId(attemptPublicId, studentPublicId))
            .thenReturn(Optional.of(attempt));
    }

    @Test
    @DisplayName("locks the ExamAttempt row and never touches TimerService")
    void playAudio_locksExamAttemptRow_neverTouchesTimerService() {
        when(attemptRepository.findWithLockById(1L)).thenReturn(Optional.of(attempt));

        AudioPlayResponse response = attemptLifecycleService.playAudio(attemptPublicId, pinnedItemPublicId, "req-1", caller);

        assertThat(response.audioUrl()).isEqualTo("https://res.cloudinary.com/test/signed-audio");
        verify(attemptRepository).findWithLockById(1L);
        verifyNoInteractions(timerService);
    }

    @Test
    @DisplayName("first play increments playCount and persists the attempt")
    void playAudio_firstPlay_incrementsPlayCount() {
        when(attemptRepository.findWithLockById(1L)).thenReturn(Optional.of(attempt));
        when(attemptRepository.save(any(ExamAttempt.class))).thenReturn(attempt);

        attemptLifecycleService.playAudio(attemptPublicId, pinnedItemPublicId, "req-1", caller);

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

        AudioPlayResponse response = attemptLifecycleService.playAudio(attemptPublicId, pinnedItemPublicId, "req-1", caller);

        assertThat(response.audioUrl()).isEqualTo("https://res.cloudinary.com/test/signed-audio");
        assertThat(attempt.getPlayCount()).isEqualTo(1);
        verify(attemptRepository, never()).save(any());
    }

    @Test
    @DisplayName("replay limit exceeded throws and persists the rejected attempt (allowed=false)")
    void playAudio_replayLimitExceeded_throws() {
        attempt.setPlayCount(2); // policy limit is 2
        when(attemptRepository.findWithLockById(1L)).thenReturn(Optional.of(attempt));
        when(attemptRepository.save(any(ExamAttempt.class))).thenReturn(attempt);

        assertThatThrownBy(() -> attemptLifecycleService.playAudio(attemptPublicId, pinnedItemPublicId, "req-2", caller))
            .isInstanceOf(ReplayLimitExceededException.class);

        assertThat(attempt.getLastPlayAllowed()).isFalse();
        assertThat(attempt.getPlayCount()).isEqualTo(2);
        assertThat(attempt.getLastPlayRequestId()).isEqualTo("req-2");
        verify(attemptRepository).save(attempt);
    }

    @Test
    @DisplayName("expired audio URL throws before any replay-count logic runs")
    void playAudio_audioUrlExpired_throws() {
        replaceItem(FakeExam.withAudio(item, "https://api.cloudinary.com/v1_1/test/video/download?type=authenticated",
                Instant.now().minusSeconds(1)));
        when(attemptRepository.findWithLockById(1L)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> attemptLifecycleService.playAudio(attemptPublicId, pinnedItemPublicId, "req-1", caller))
            .isInstanceOf(AudioUrlExpiredException.class);

        assertThat(attempt.getPlayCount()).isZero();
        assertThat(attempt.getLastPlayRequestId()).isNull();
        verify(attemptRepository, never()).save(any());
    }

    @Test
    @DisplayName("public legacy audio remains playable when its stored zero-TTL timestamp is in the past")
    void playAudio_publicLegacyAudio_ignoresStaleExpiryTimestamp() {
        replaceItem(FakeExam.withAudio(item, "https://cdn.example.com/public/lecture.mp3",
                Instant.now().minusSeconds(1)));
        when(attemptRepository.findWithLockById(1L)).thenReturn(Optional.of(attempt));
        when(attemptRepository.save(any(ExamAttempt.class))).thenReturn(attempt);

        AudioPlayResponse response = attemptLifecycleService.playAudio(
                attemptPublicId, pinnedItemPublicId, "req-1", caller);

        assertThat(response.audioUrl()).isEqualTo("https://cdn.example.com/public/lecture.mp3");
    }

    @Test
    @DisplayName("an unknown item id throws NotCurrentTaskException without consuming replay allowance")
    void playAudio_notCurrentItem_throws() {
        when(attemptRepository.findWithLockById(1L)).thenReturn(Optional.of(attempt));
        UUID staleItemPublicId = UUID.randomUUID();

        assertThatThrownBy(() -> attemptLifecycleService.playAudio(attemptPublicId, staleItemPublicId, "req-1", caller))
            .isInstanceOf(NotCurrentTaskException.class);

        assertThat(attempt.getPlayCount()).isZero();
        assertThat(attempt.getLastPlayRequestId()).isNull();
        verify(attemptRepository, never()).save(any());
        verifyNoInteractions(timerService);
    }

    @Test
    @DisplayName("client navigation to another item of the same exam allows audio without advancing the task timer")
    void playAudio_differentOrderIndex_allowsClientNavigation() {
        exam.items.clear();
        item = exam.add(FakeExam.withOrderIndex(item, 1));
        when(attemptRepository.findWithLockById(1L)).thenReturn(Optional.of(attempt));

        AudioPlayResponse response = attemptLifecycleService.playAudio(attemptPublicId, pinnedItemPublicId, "req-1", caller);

        assertThat(response.audioUrl()).isEqualTo("https://res.cloudinary.com/test/signed-audio");
        assertThat(attempt.getCurrentOrderIndex()).isZero();
        assertThat(attempt.getPlayCount()).isEqualTo(1);
        verify(attemptRepository).save(attempt);
        verifyNoInteractions(timerService);
    }

    @Test
    @DisplayName("an already-completed attempt throws before acquiring the lock")
    void playAudio_attemptAlreadyComplete_throwsWithoutLocking() {
        attempt.submit();

        assertThatThrownBy(() -> attemptLifecycleService.playAudio(attemptPublicId, pinnedItemPublicId, "req-1", caller))
            .isInstanceOf(AttemptAlreadyCompleteException.class);

        verify(attemptRepository, never()).findWithLockById(any());
        verifyNoInteractions(timerService);
    }

    @Test
    @DisplayName("UNLIMITED replay policy (limit sentinel < 0) never rejects regardless of playCount")
    void playAudio_unlimitedPolicy_neverRejects() {
        attempt.setReplayPolicyType("UNLIMITED");
        attempt.setPlayCount(50);
        when(attemptRepository.findWithLockById(1L)).thenReturn(Optional.of(attempt));
        when(attemptRepository.save(any(ExamAttempt.class))).thenReturn(attempt);

        AudioPlayResponse response = attemptLifecycleService.playAudio(attemptPublicId, pinnedItemPublicId, "req-51", caller);

        assertThat(response.audioUrl()).isEqualTo("https://res.cloudinary.com/test/signed-audio");
        assertThat(attempt.getPlayCount()).isEqualTo(51);
    }

    private void replaceItem(ExamItemView replacement) {
        exam.items.clear();
        item = exam.add(replacement);
    }
}
