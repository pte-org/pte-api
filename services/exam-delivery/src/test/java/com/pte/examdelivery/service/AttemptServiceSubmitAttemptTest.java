package com.pte.examdelivery.service;

import com.pte.common.security.CurrentUser;
import com.pte.examdelivery.config.EncryptionKeyProvider;
import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.domain.PinnedExamSnapshot;
import com.pte.examdelivery.domain.enums.AttemptStatus;
import com.pte.examdelivery.domain.exception.AttemptAlreadyCompleteException;
import com.pte.examdelivery.dto.response.AttemptTaskResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@code AttemptService.submitAttempt} — no dedicated test existed
 * before client-side-exam-timer Phase 1 (a pre-existing gap). Added
 * specifically because a code-reviewer 2nd-pass finding caught {@code
 * submitAttempt} as an unlocked writer of the same {@code @Version}-guarded
 * {@code ExamAttempt} row that {@code playAudio}/{@code processAnswer}/
 * {@code advanceUntilLiveOrComplete} now lock — fixed by locking inside
 * {@code completeAttempt} itself (the single choke point all four paths funnel
 * through), verified here directly.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttemptService.submitAttempt (client-side-exam-timer Phase 1)")
class AttemptServiceSubmitAttemptTest {

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
    private ExamAttempt attempt;

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

        AttemptTaskResponse response = attemptService.submitAttempt(attemptPublicId, caller);

        assertThat(response.completed()).isTrue();
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
        verify(attemptRepository).findWithLockById(1L);
        verify(attemptRepository).save(attempt);
    }

    @Test
    @DisplayName("an already-completed attempt throws without locking or re-completing")
    void submitAttempt_alreadyComplete_throwsWithoutLocking() {
        attempt.submit();

        assertThatThrownBy(() -> attemptService.submitAttempt(attemptPublicId, caller))
            .isInstanceOf(AttemptAlreadyCompleteException.class);

        verify(attemptRepository, never()).findWithLockById(any());
        verify(attemptRepository, never()).save(any());
    }
}
