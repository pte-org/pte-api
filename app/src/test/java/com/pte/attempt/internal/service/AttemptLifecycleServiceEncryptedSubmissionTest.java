package com.pte.attempt.internal.service;

import com.pte.attempt.internal.config.EncryptionKeyProvider;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.PinnedExamSnapshot;
import com.pte.attempt.domain.PinnedItem;
import com.pte.attempt.internal.exception.AnswerIntegrityLevelMismatchException;
import com.pte.attempt.internal.dto.request.EncryptedSubmissionRequest;
import com.pte.attempt.internal.dto.request.SubmitAnswerRequest;
import com.pte.attempt.internal.dto.response.AttemptTaskResponse;
import com.pte.attempt.internal.mapper.AttemptMapper;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.attempt.internal.repository.PinnedItemRepository;
import com.pte.attempt.internal.service.cache.PinnedSnapshotCacheService;
import com.pte.shared.security.CurrentUser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.security.PrivateKey;
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
 * Ported from services/exam-delivery's own AttemptServicePhase2Test — same
 * coverage, new package. Covers conditional submission routing:
 * submitAnswer rejects STRICT-pinned attempts, submitEncryptedAnswer rejects
 * STANDARD-pinned attempts, and a valid encrypted submission on a
 * STRICT-pinned attempt decrypts and processes via EncryptionKeyProvider's
 * private key.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttemptLifecycleService (encrypted submissions)")
class AttemptLifecycleServiceEncryptedSubmissionTest {

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
    private UUID tenantId;
    private CurrentUser caller;

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

        assertThatThrownBy(() -> attemptLifecycleService.submitAnswer(attemptPublicId, request, caller))
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

        assertThatThrownBy(() -> attemptLifecycleService.submitEncryptedAnswer(attemptPublicId, request, caller))
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
        PrivateKey privateKey = mock(PrivateKey.class);

        when(attemptRepository.findWithPinnedByPublicIdAndStudentPublicId(attemptPublicId, studentPublicId))
            .thenReturn(Optional.of(attempt));
        when(attemptRepository.findWithLockById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(pinnedItemRepository.findByPinnedSnapshotIdAndOrderIndex(attempt.getPinnedSnapshot().getId(), 0))
            .thenReturn(Optional.of(item));
        when(encryptionKeyProvider.getPrivateKey()).thenReturn(privateKey);

        EncryptedSubmissionRequest request = new EncryptedSubmissionRequest(
            pinnedItemPublicId, "wrappedKeyBase64", "ivBase64", "ciphertextBase64");
        when(submissionDecryptionService.decrypt(eq(request), eq(privateKey))).thenReturn(decryptedPlaintext);

        // Only pinned item -> nextIndex >= totalItems -> attempt completes.
        when(pinnedItemRepository.countByPinnedSnapshotId(attempt.getPinnedSnapshot().getId())).thenReturn(1L);
        when(attemptRepository.save(any(ExamAttempt.class))).thenReturn(attempt);

        AttemptTaskResponse response = attemptLifecycleService.submitEncryptedAnswer(attemptPublicId, request, caller);

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
        PrivateKey privateKey = mock(PrivateKey.class);

        when(attemptRepository.findWithPinnedByPublicIdAndStudentPublicId(attemptPublicId, studentPublicId))
            .thenReturn(Optional.of(attempt));
        when(attemptRepository.findWithLockById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(pinnedItemRepository.findByPinnedSnapshotIdAndOrderIndex(attempt.getPinnedSnapshot().getId(), 0))
            .thenReturn(Optional.of(item));
        when(encryptionKeyProvider.getPrivateKey()).thenReturn(privateKey);
        when(pinnedItemRepository.countByPinnedSnapshotId(attempt.getPinnedSnapshot().getId())).thenReturn(1L);
        when(attemptRepository.save(any(ExamAttempt.class))).thenReturn(attempt);

        EncryptedSubmissionRequest request = new EncryptedSubmissionRequest(
            pinnedItemPublicId, "wrappedKeyBase64", "ivBase64", "ciphertextBase64");
        when(submissionDecryptionService.decrypt(eq(request), eq(privateKey))).thenReturn("answer");

        attemptLifecycleService.submitEncryptedAnswer(attemptPublicId, request, caller);

        verify(submissionDecryptionService).decrypt(request, privateKey);
    }

    private ExamAttempt createInProgressAttempt(UUID publicId, String answerIntegrityLevel) {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(1L);
        attempt.setPublicId(publicId);
        attempt.setStudentPublicId(studentPublicId);
        attempt.setTenantId(tenantId);
        attempt.begin();
        attempt.setCurrentOrderIndex(0);

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
}
