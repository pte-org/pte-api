package com.pte.attempt.internal.service;

import com.pte.attempt.internal.config.EncryptionKeyProvider;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.PinnedExamSnapshot;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.internal.exception.AttemptAlreadyCompleteException;
import com.pte.attempt.internal.dto.response.AttemptTaskResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ported from services/exam-delivery's own AttemptServiceSubmitAttemptTest —
 * same coverage, new package. Verifies {@code submitAttempt} locks the {@code
 * @Version}-guarded {@code ExamAttempt} row before completing it — the single
 * choke point {@code completeAttempt} funnels every completion path through.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttemptLifecycleService.submitAttempt")
class AttemptLifecycleServiceSubmitAttemptTest {

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
    private ExamAttempt attempt;

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
        UUID tenantId = UUID.randomUUID();
        caller = new CurrentUser(studentPublicId, tenantId, List.of("student"));
        attemptPublicId = UUID.randomUUID();

        attempt = new ExamAttempt();
        attempt.setId(1L);
        attempt.setPublicId(attemptPublicId);
        attempt.setStudentPublicId(studentPublicId);
        attempt.setTenantId(tenantId);
        attempt.begin();
        attempt.setPinnedSnapshot(new PinnedExamSnapshot());

        when(attemptRepository.findWithPinnedByPublicIdAndStudentPublicId(attemptPublicId, studentPublicId))
            .thenReturn(Optional.of(attempt));
    }

    @Test
    @DisplayName("locks the ExamAttempt row before completing it")
    void submitAttempt_locksBeforeCompleting() {
        when(attemptRepository.findWithLockById(1L)).thenReturn(Optional.of(attempt));
        when(attemptRepository.save(any(ExamAttempt.class))).thenReturn(attempt);

        AttemptTaskResponse response = attemptLifecycleService.submitAttempt(attemptPublicId, caller);

        assertThat(response.completed()).isTrue();
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
        verify(attemptRepository).findWithLockById(1L);
        verify(attemptRepository).save(attempt);
    }

    @Test
    @DisplayName("an already-completed attempt throws without locking or re-completing")
    void submitAttempt_alreadyComplete_throwsWithoutLocking() {
        attempt.submit();

        assertThatThrownBy(() -> attemptLifecycleService.submitAttempt(attemptPublicId, caller))
            .isInstanceOf(AttemptAlreadyCompleteException.class);

        verify(attemptRepository, never()).findWithLockById(any());
        verify(attemptRepository, never()).save(any());
    }
}
