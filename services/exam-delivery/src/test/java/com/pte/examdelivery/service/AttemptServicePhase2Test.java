package com.pte.examdelivery.service;

import com.pte.common.security.CurrentUser;
import com.pte.examdelivery.config.EncryptionKeyProvider;
import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.domain.PinnedExamSnapshot;
import com.pte.examdelivery.domain.PinnedItem;
import com.pte.examdelivery.domain.TimerState;
import com.pte.examdelivery.domain.exception.AnswerIntegrityLevelMismatchException;
import com.pte.examdelivery.dto.request.EncryptedSubmissionRequest;
import com.pte.examdelivery.dto.request.SubmitAnswerRequest;
import com.pte.examdelivery.dto.response.AttemptTaskResponse;
import com.pte.examdelivery.mapper.AttemptMapper;
import com.pte.examdelivery.messaging.outbox.OutboxWriter;
import com.pte.examdelivery.repository.AttemptAnswerRepository;
import com.pte.examdelivery.repository.ExamAttemptRepository;
import com.pte.examdelivery.repository.PinnedItemRepository;
import com.pte.examdelivery.service.cache.PinnedSnapshotCacheService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.security.PrivateKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@code AttemptService}, Phase 2: Conditional Submission Routing & Decryption.
 *
 * Covers:
 * - submitAnswer rejects STRICT-pinned attempts (throws AnswerIntegrityLevelMismatchException)
 * - submitEncryptedAnswer rejects STANDARD-pinned attempts (throws AnswerIntegrityLevelMismatchException)
 * - submitEncryptedAnswer on a STRICT-pinned attempt decrypts and processes the answer
 * - Decryption is performed with the private key from EncryptionKeyProvider
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttemptService (Phase 2: Encrypted Submissions)")
class AttemptServicePhase2Test {

    @Mock
    private ExamAttemptRepository attemptRepository;

    @Mock
    private PinnedItemRepository pinnedItemRepository;

    @Mock
    private AttemptAnswerRepository attemptAnswerRepository;

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

    private AttemptService attemptService;
    private AttemptMapper attemptMapper;

    private UUID studentPublicId;
    private UUID tenantId;
    private CurrentUser caller;

    @BeforeEach
    void setUp() {
        attemptMapper = new AttemptMapper(JsonMapper.builder().build());

        attemptService = new AttemptService(
            attemptRepository,
            pinnedItemRepository,
            attemptAnswerRepository,
            snapshotPinService,
            cacheService,
            timerService,
            answerSubmitService,
            attemptMapper,
            outboxWriter,
            encryptionKeyProvider,
            submissionDecryptionService
        );

        studentPublicId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        caller = new CurrentUser(studentPublicId, tenantId, List.of("student"));
    }

    @Test
    @DisplayName("submitAnswer on STRICT-pinned attempt throws AnswerIntegrityLevelMismatchException")
    void submitAnswerOnStrictPinnedAttemptThrows() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID pinnedItemPublicId = UUID.randomUUID();
        ExamAttempt attempt = createInProgressAttempt(attemptPublicId, "STRICT");

        when(attemptRepository.findWithPinnedByPublicIdAndStudentPublicId(attemptPublicId, studentPublicId))
            .thenReturn(Optional.of(attempt));

        SubmitAnswerRequest request = new SubmitAnswerRequest(pinnedItemPublicId, "2");

        assertThatThrownBy(() -> attemptService.submitAnswer(attemptPublicId, request, caller))
            .isInstanceOf(AnswerIntegrityLevelMismatchException.class);
    }

    @Test
    @DisplayName("submitEncryptedAnswer on STANDARD-pinned attempt throws AnswerIntegrityLevelMismatchException")
    void submitEncryptedAnswerOnStandardPinnedAttemptThrows() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID pinnedItemPublicId = UUID.randomUUID();
        ExamAttempt attempt = createInProgressAttempt(attemptPublicId, "STANDARD");

        when(attemptRepository.findWithPinnedByPublicIdAndStudentPublicId(attemptPublicId, studentPublicId))
            .thenReturn(Optional.of(attempt));

        EncryptedSubmissionRequest request = new EncryptedSubmissionRequest(
            pinnedItemPublicId, "wrappedKeyBase64", "ivBase64", "ciphertextBase64");

        assertThatThrownBy(() -> attemptService.submitEncryptedAnswer(attemptPublicId, request, caller))
            .isInstanceOf(AnswerIntegrityLevelMismatchException.class);
    }

    @Test
    @DisplayName("submitEncryptedAnswer on STRICT-pinned attempt with valid encrypted payload decrypts and completes")
    void submitEncryptedAnswerOnStrictPinnedAttemptSucceeds() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID pinnedItemPublicId = UUID.randomUUID();
        String decryptedPlaintext = "2";

        ExamAttempt attempt = createInProgressAttempt(attemptPublicId, "STRICT");
        PinnedItem item = createPinnedItem(pinnedItemPublicId, attempt.getPinnedSnapshot());
        TimerState timer = createTimerState(0);
        PrivateKey privateKey = mock(PrivateKey.class);

        when(attemptRepository.findWithPinnedByPublicIdAndStudentPublicId(attemptPublicId, studentPublicId))
            .thenReturn(Optional.of(attempt));
        when(timerService.getState(attempt.getId())).thenReturn(timer);
        when(pinnedItemRepository.findByPinnedSnapshotIdAndOrderIndex(attempt.getPinnedSnapshot().getId(), 0))
            .thenReturn(Optional.of(item));
        when(timerService.isResponseWindowExpired(timer)).thenReturn(false);
        when(encryptionKeyProvider.getPrivateKey()).thenReturn(privateKey);

        EncryptedSubmissionRequest request = new EncryptedSubmissionRequest(
            pinnedItemPublicId, "wrappedKeyBase64", "ivBase64", "ciphertextBase64");
        when(submissionDecryptionService.decrypt(eq(request), eq(privateKey))).thenReturn(decryptedPlaintext);

        // Only pinned item -> nextIndex >= totalItems -> attempt completes.
        when(pinnedItemRepository.countByPinnedSnapshotId(attempt.getPinnedSnapshot().getId())).thenReturn(1L);
        when(attemptRepository.save(any(ExamAttempt.class))).thenReturn(attempt);

        AttemptTaskResponse response = attemptService.submitEncryptedAnswer(attemptPublicId, request, caller);

        assertThat(response.attemptPublicId()).isEqualTo(attemptPublicId);
        assertThat(response.completed()).isTrue();
        assertThat(response.encryptionPublicKey()).isNull();
        verify(answerSubmitService).submit(attempt, item, decryptedPlaintext);
    }

    @Test
    @DisplayName("submitEncryptedAnswer decrypts using the private key from EncryptionKeyProvider")
    void submitEncryptedAnswerUsesPrivateKeyFromProvider() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID pinnedItemPublicId = UUID.randomUUID();

        ExamAttempt attempt = createInProgressAttempt(attemptPublicId, "STRICT");
        PinnedItem item = createPinnedItem(pinnedItemPublicId, attempt.getPinnedSnapshot());
        TimerState timer = createTimerState(0);
        PrivateKey privateKey = mock(PrivateKey.class);

        when(attemptRepository.findWithPinnedByPublicIdAndStudentPublicId(attemptPublicId, studentPublicId))
            .thenReturn(Optional.of(attempt));
        when(timerService.getState(attempt.getId())).thenReturn(timer);
        when(pinnedItemRepository.findByPinnedSnapshotIdAndOrderIndex(attempt.getPinnedSnapshot().getId(), 0))
            .thenReturn(Optional.of(item));
        when(timerService.isResponseWindowExpired(timer)).thenReturn(false);
        when(encryptionKeyProvider.getPrivateKey()).thenReturn(privateKey);
        when(pinnedItemRepository.countByPinnedSnapshotId(attempt.getPinnedSnapshot().getId())).thenReturn(1L);
        when(attemptRepository.save(any(ExamAttempt.class))).thenReturn(attempt);

        EncryptedSubmissionRequest request = new EncryptedSubmissionRequest(
            pinnedItemPublicId, "wrappedKeyBase64", "ivBase64", "ciphertextBase64");
        when(submissionDecryptionService.decrypt(eq(request), eq(privateKey))).thenReturn("answer");

        attemptService.submitEncryptedAnswer(attemptPublicId, request, caller);

        verify(submissionDecryptionService).decrypt(request, privateKey);
    }

    // Helper methods

    private ExamAttempt createInProgressAttempt(UUID publicId, String answerIntegrityLevel) {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(1L);
        attempt.setPublicId(publicId);
        attempt.setStudentPublicId(studentPublicId);
        attempt.setTenantId(tenantId);
        attempt.begin();

        PinnedExamSnapshot snapshot = new PinnedExamSnapshot();
        snapshot.setId(1L);
        snapshot.setPublicId(UUID.randomUUID());
        snapshot.setAnswerIntegrityLevel(answerIntegrityLevel);
        attempt.setPinnedSnapshot(snapshot);
        return attempt;
    }

    private PinnedItem createPinnedItem(UUID publicId, PinnedExamSnapshot snapshot) {
        PinnedItem item = new PinnedItem();
        item.setId(1L);
        item.setPublicId(publicId);
        item.setOrderIndex(0);
        item.setSection("READING");
        item.setTaskType("MC_READING_SINGLE");
        item.setTitle("Test task");
        item.setPromptText("Test prompt");
        item.setOptionsJson("[]");
        item.setPrepSeconds(30);
        item.setResponseSeconds(60);
        item.setPinnedSnapshot(snapshot);
        return item;
    }

    private TimerState createTimerState(int orderIndex) {
        TimerState timer = new TimerState();
        timer.setId(1L);
        timer.setCurrentOrderIndex(orderIndex);
        timer.setPrepDeadline(Instant.now());
        timer.setResponseDeadline(Instant.now().plusSeconds(60));
        return timer;
    }
}
