package com.pte.examdelivery.service;

import com.pte.common.security.CurrentUser;
import com.pte.examdelivery.config.EncryptionKeyProvider;
import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.domain.exception.AttemptAlreadyCompleteException;
import com.pte.examdelivery.domain.exception.AttemptNotFoundException;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@code AttemptService.recordHeartbeat} (client-side-exam-timer Phase 2,
 * FR-04): ownership + status checks only, no task content — and confirms the
 * lightweight {@code findByPublicIdAndStudentPublicId} lookup is used, not the
 * heavier pinned-snapshot-eager-fetching {@code findWithPinnedByPublicIdAndStudentPublicId}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttemptService.recordHeartbeat (client-side-exam-timer Phase 2)")
class AttemptServiceHeartbeatTest {

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

        attemptService.recordHeartbeat(attemptPublicId, caller);

        verify(heartbeatService).recordHeartbeat(attempt);
        verify(attemptRepository, never()).findWithPinnedByPublicIdAndStudentPublicId(any(), any());
    }

    @Test
    @DisplayName("a non-owner (or nonexistent attempt) throws AttemptNotFoundException, never records")
    void recordHeartbeat_notOwned_throwsWithoutRecording() {
        when(attemptRepository.findByPublicIdAndStudentPublicId(attemptPublicId, studentPublicId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> attemptService.recordHeartbeat(attemptPublicId, caller))
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

        assertThatThrownBy(() -> attemptService.recordHeartbeat(attemptPublicId, caller))
            .isInstanceOf(AttemptAlreadyCompleteException.class);

        verify(heartbeatService, never()).recordHeartbeat(any());
    }
}
