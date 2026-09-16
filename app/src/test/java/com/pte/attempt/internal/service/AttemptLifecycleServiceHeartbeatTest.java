package com.pte.attempt.internal.service;

import com.pte.attempt.internal.config.EncryptionKeyProvider;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.internal.exception.AttemptAlreadyCompleteException;
import com.pte.attempt.internal.exception.AttemptNotFoundException;
import com.pte.attempt.internal.mapper.AttemptMapper;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.attempt.internal.repository.PinnedItemRepository;
import com.pte.attempt.internal.service.cache.PinnedSnapshotCacheService;
import com.pte.shared.security.CurrentUser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ported from services/exam-delivery's own AttemptServiceHeartbeatTest —
 * same coverage, new package. Confirms {@code recordHeartbeat} uses the
 * lightweight {@code findByPublicIdAndStudentPublicId} lookup, not the
 * heavier pinned-snapshot-eager-fetching one.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttemptLifecycleService.recordHeartbeat")
class AttemptLifecycleServiceHeartbeatTest {

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
    private EncryptionKeyProvider encryptionKeyProvider;

    @Mock
    private SubmissionDecryptionService submissionDecryptionService;

    @Mock
    private HeartbeatService heartbeatService;

    private AttemptLifecycleService attemptLifecycleService;
    private UUID studentPublicId;
    private CurrentUser caller;
    private UUID attemptPublicId;

    @BeforeEach
    void setUp() {
        AttemptMapper attemptMapper = new AttemptMapper(JsonMapper.builder().build());
        attemptLifecycleService = new AttemptLifecycleService(
            attemptRepository,
            pinnedItemRepository,
            snapshotPinService,
            cacheService,
            timerService,
            answerSubmitService,
            attemptMapper,
            encryptionKeyProvider,
            submissionDecryptionService,
            heartbeatService
        );

        studentPublicId = UUID.randomUUID();
        caller = new CurrentUser(studentPublicId, UUID.randomUUID(), List.of("student"));
        attemptPublicId = UUID.randomUUID();
    }

    @Test
    @DisplayName("owning student on an IN_PROGRESS attempt records a heartbeat via the lightweight lookup")
    void recordHeartbeat_owningStudentInProgress_succeeds() {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(1L);
        attempt.begin();
        when(attemptRepository.findByPublicIdAndStudentPublicId(attemptPublicId, studentPublicId))
            .thenReturn(Optional.of(attempt));

        attemptLifecycleService.recordHeartbeat(attemptPublicId, caller);

        verify(heartbeatService).recordHeartbeat(attempt);
        verify(attemptRepository, never()).findWithPinnedByPublicIdAndStudentPublicId(any(), any());
    }

    @Test
    @DisplayName("a non-owner (or nonexistent attempt) throws AttemptNotFoundException, never records")
    void recordHeartbeat_notOwned_throwsWithoutRecording() {
        when(attemptRepository.findByPublicIdAndStudentPublicId(attemptPublicId, studentPublicId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> attemptLifecycleService.recordHeartbeat(attemptPublicId, caller))
            .isInstanceOf(AttemptNotFoundException.class);

        verify(heartbeatService, never()).recordHeartbeat(any());
    }

    @Test
    @DisplayName("a non-IN_PROGRESS attempt throws AttemptAlreadyCompleteException, never records")
    void recordHeartbeat_notInProgress_throwsWithoutRecording() {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(1L);
        attempt.begin();
        attempt.submit();
        when(attemptRepository.findByPublicIdAndStudentPublicId(attemptPublicId, studentPublicId))
            .thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> attemptLifecycleService.recordHeartbeat(attemptPublicId, caller))
            .isInstanceOf(AttemptAlreadyCompleteException.class);

        verify(heartbeatService, never()).recordHeartbeat(any());
    }
}
