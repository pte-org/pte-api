package com.pte.examdelivery.service;

import com.pte.common.security.CurrentUser;
import com.pte.examdelivery.config.EncryptionKeyProvider;
import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.domain.PinnedExamSnapshot;
import com.pte.examdelivery.domain.PinnedItem;
import com.pte.examdelivery.domain.TimerState;
import com.pte.examdelivery.domain.enums.AttemptStatus;
import com.pte.examdelivery.dto.request.StartAttemptRequest;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@code AttemptService.startAttempt} (via {@code createAndPin}),
 * verifying that encryption public key is populated correctly based on {@code answerIntegrityLevel}.
 *
 * Phase 1: RSA Keypair Provisioning & StartAttempt Public Key Exposure
 * Success Criteria:
 * - StartAttempt on a STRICT-pinned attempt returns a non-null, non-empty encryptionPublicKey
 * - StartAttempt on a STANDARD-pinned attempt returns null/absent for that field
 * - All other existing StartAttempt assertions still pass unchanged
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttemptService")
class AttemptServiceTest {

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

    private static final String TEST_PUBLIC_KEY_BASE64 =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAu1SU1LfVLfHCozMxH2Mo" +
        "4lgOP+KXvqbYGsVQ3e1xLjDln2S4C2iYVCpnzyjuuSZgZn59SqlQys1SCyOnS5An" +
        "294S6sGUIrkV+7k6pvJT5FQzCyd28ydkFNcDBfFg8i4QAbifBG4Ud4GUci9zoxaz" +
        "dMHdC2W+KcHFvL5CELyUjfrjhNQGgF+yp2yMzdMPFgisv3yilQ/9BAkGRJef8vMY" +
        "+lbTW0qUpmVqX77wT+oe9S/KlzRS4Cj0FYNe5j2FlpFaznJz+AOnWW/30Gyt3D/w" +
        "MFObRpKaVQw8bw90fEDVll0dkNz3Br709RfKjXzEx90fa9qVZGealkxTZ6D4CkWG" +
        "EwIDAQAB";

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

        // Set up standard mock responses
        when(attemptRepository.save(any(ExamAttempt.class)))
            .thenAnswer(invocation -> {
                ExamAttempt attempt = invocation.getArgument(0);
                if (attempt.getId() == null) {
                    attempt.setId(1L);
                }
                if (attempt.getPublicId() == null) {
                    attempt.setPublicId(UUID.randomUUID());
                }
                return attempt;
            });
    }

    /**
     * Test: startAttempt on a STRICT-pinned attempt returns a non-null, non-empty encryptionPublicKey.
     * This verifies Step 5 of the phase file: the createAndPin code path populates encryptionPublicKey
     * from EncryptionKeyProvider only when answerIntegrityLevel == "STRICT".
     *
     * Note: This test will fail until:
     * 1. EncryptionKeyProvider is created
     * 2. AttemptService is updated to inject and use EncryptionKeyProvider
     * 3. The createAndPin method is updated to pass encryptionPublicKey to the mapper
     */
    @Test
    @DisplayName("startAttempt on STRICT-pinned attempt returns non-null encryptionPublicKey")
    void startAttemptStrictPinnedReturnsPublicKey() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(studentPublicId, tenantId, List.of("student"));

        // Set up a STRICT-pinned snapshot
        PinnedExamSnapshot pinnedSnapshot = createPinnedSnapshot(tenantId, "STRICT");
        PinnedItem firstItem = pinnedSnapshot.getItems().get(0);

        when(snapshotPinService.pin(any(), any(), any())).thenReturn(pinnedSnapshot);
        when(timerService.startTaskTimer(any(), any(), any()))
            .thenReturn(createTimerState(0));
        when(encryptionKeyProvider.getPublicKeyBase64()).thenReturn(TEST_PUBLIC_KEY_BASE64);

        StartAttemptRequest request = new StartAttemptRequest(sessionPublicId, true);

        // Execute
        AttemptTaskResponse response = attemptService.startAttempt(request, caller);

        // Assert: STRICT attempt should have non-null encryptionPublicKey
        assertThat(response.encryptionPublicKey())
            .isNotNull()
            .isNotEmpty();
    }

    /**
     * Test: startAttempt on a STANDARD-pinned attempt returns null for encryptionPublicKey.
     * This verifies that STANDARD attempts maintain existing behavior (no public key field).
     * Success criterion: "encryptionPublicKey is null/absent for STANDARD-pinned attempts,
     * with zero change to any other field or existing STANDARD-path behavior."
     */
    @Test
    @DisplayName("startAttempt on STANDARD-pinned attempt returns null encryptionPublicKey")
    void startAttemptStandardPinnedReturnsNullPublicKey() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(studentPublicId, tenantId, List.of("student"));

        // Set up a STANDARD-pinned snapshot
        PinnedExamSnapshot pinnedSnapshot = createPinnedSnapshot(tenantId, "STANDARD");

        when(snapshotPinService.pin(any(), any(), any())).thenReturn(pinnedSnapshot);
        when(timerService.startTaskTimer(any(), any(), any()))
            .thenReturn(createTimerState(0));

        StartAttemptRequest request = new StartAttemptRequest(sessionPublicId, true);

        // Execute
        AttemptTaskResponse response = attemptService.startAttempt(request, caller);

        // Assert: STANDARD attempt should have null encryptionPublicKey
        assertThat(response.encryptionPublicKey()).isNull();
    }

    /**
     * Test: startAttempt on STANDARD-pinned attempt preserves all other response fields.
     * This verifies that adding encryptionPublicKey doesn't break existing STANDARD behavior.
     */
    @Test
    @DisplayName("startAttempt on STANDARD-pinned attempt preserves all other response fields")
    void startAttemptStandardPinnedPreservesOtherFields() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(studentPublicId, tenantId, List.of("student"));

        PinnedExamSnapshot pinnedSnapshot = createPinnedSnapshot(tenantId, "STANDARD");
        PinnedItem firstItem = pinnedSnapshot.getItems().get(0);

        when(snapshotPinService.pin(any(), any(), any())).thenReturn(pinnedSnapshot);
        when(timerService.startTaskTimer(any(), any(), any()))
            .thenReturn(createTimerState(0));

        StartAttemptRequest request = new StartAttemptRequest(sessionPublicId, true);

        // Execute
        AttemptTaskResponse response = attemptService.startAttempt(request, caller);

        // Assert: all standard fields are populated
        assertThat(response.attemptPublicId()).isNotNull();
        assertThat(response.attemptStatus()).isEqualTo(AttemptStatus.IN_PROGRESS.name());
        assertThat(response.completed()).isFalse();
        assertThat(response.task()).isNotNull();
        assertThat(response.task().pinnedItemPublicId()).isEqualTo(firstItem.getPublicId());
    }

    /**
     * Test: startAttempt on STRICT-pinned attempt preserves all existing fields.
     * This verifies that adding encryptionPublicKey is additive, not breaking.
     */
    @Test
    @DisplayName("startAttempt on STRICT-pinned attempt preserves all other response fields")
    void startAttemptStrictPinnedPreservesOtherFields() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(studentPublicId, tenantId, List.of("student"));

        PinnedExamSnapshot pinnedSnapshot = createPinnedSnapshot(tenantId, "STRICT");
        PinnedItem firstItem = pinnedSnapshot.getItems().get(0);

        when(snapshotPinService.pin(any(), any(), any())).thenReturn(pinnedSnapshot);
        when(timerService.startTaskTimer(any(), any(), any()))
            .thenReturn(createTimerState(0));
        when(encryptionKeyProvider.getPublicKeyBase64()).thenReturn(TEST_PUBLIC_KEY_BASE64);

        StartAttemptRequest request = new StartAttemptRequest(sessionPublicId, true);

        // Execute
        AttemptTaskResponse response = attemptService.startAttempt(request, caller);

        // Assert: all standard fields are populated
        assertThat(response.attemptPublicId()).isNotNull();
        assertThat(response.attemptStatus()).isEqualTo(AttemptStatus.IN_PROGRESS.name());
        assertThat(response.completed()).isFalse();
        assertThat(response.task()).isNotNull();
        assertThat(response.task().pinnedItemPublicId()).isEqualTo(firstItem.getPublicId());
    }

    // Helper methods

    private PinnedExamSnapshot createPinnedSnapshot(UUID tenantId, String answerIntegrityLevel) {
        PinnedExamSnapshot snapshot = new PinnedExamSnapshot();
        snapshot.setId(1L);
        snapshot.setPublicId(UUID.randomUUID());
        snapshot.setSourceSnapshotPublicId(UUID.randomUUID());
        snapshot.setSourceSessionPublicId(UUID.randomUUID());
        snapshot.setTenantId(tenantId);
        snapshot.setReplayPolicyType("UNLIMITED");
        snapshot.setDeviceCheckRequired(false);
        snapshot.setProctorRequired(false);
        snapshot.setAnswerIntegrityLevel(answerIntegrityLevel);

        PinnedItem item = new PinnedItem();
        item.setId(1L);
        item.setPublicId(UUID.randomUUID());
        item.setOrderIndex(0);
        item.setSection("READING");
        item.setTaskType("MC_READING_SINGLE");
        item.setTitle("Sample title");
        item.setPromptText("Sample prompt");
        item.setOptionsJson("[]");
        item.setPrepSeconds(30);
        item.setResponseSeconds(60);
        item.setPinnedSnapshot(snapshot);

        snapshot.addItem(item);
        return snapshot;
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
