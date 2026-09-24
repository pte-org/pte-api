package com.pte.attempt.internal.service;

import com.pte.attempt.internal.config.EncryptionKeyProvider;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.PinnedExamSnapshot;
import com.pte.attempt.domain.PinnedItem;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.internal.dto.request.StartAttemptRequest;
import com.pte.attempt.internal.dto.response.AttemptTaskResponse;
import com.pte.attempt.internal.mapper.AttemptMapper;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.attempt.internal.repository.PinnedItemRepository;
import com.pte.attempt.internal.service.cache.PinnedSnapshotCacheService;
import com.pte.shared.security.CurrentUser;
import com.pte.session.SessionService;
import com.pte.session.internal.exception.NotEntitledException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Ported from services/exam-delivery's own AttemptServiceTest — same
 * coverage, minus OutboxWriter (no outbox in the monolith; startAttempt is
 * still the ONE call that pins a snapshot). Covers {@code startAttempt}'s
 * {@code encryptionPublicKey} population: non-null only for STRICT-pinned
 * attempts.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttemptLifecycleService")
class AttemptLifecycleServiceTest {

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

    }

    @Test
    @DisplayName("startAttempt on STRICT-pinned attempt returns non-null encryptionPublicKey")
    void startAttemptStrictPinnedReturnsPublicKey() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(studentPublicId, tenantId, List.of("student"));

        PinnedExamSnapshot pinnedSnapshot = createPinnedSnapshot(tenantId, "STRICT");

        stubAttemptSave();
        when(snapshotPinService.pin(any(), any(), any())).thenReturn(pinnedSnapshot);
        when(encryptionKeyProvider.getPublicKeyBase64()).thenReturn(TEST_PUBLIC_KEY_BASE64);

        StartAttemptRequest request = new StartAttemptRequest(sessionPublicId, true);

        AttemptTaskResponse response = attemptLifecycleService.startAttempt(request, caller);

        assertThat(response.encryptionPublicKey())
            .isNotNull()
            .isNotEmpty();
    }

    @Test
    @DisplayName("startAttempt on STANDARD-pinned attempt returns null encryptionPublicKey")
    void startAttemptStandardPinnedReturnsNullPublicKey() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(studentPublicId, tenantId, List.of("student"));

        PinnedExamSnapshot pinnedSnapshot = createPinnedSnapshot(tenantId, "STANDARD");

        stubAttemptSave();
        when(snapshotPinService.pin(any(), any(), any())).thenReturn(pinnedSnapshot);

        StartAttemptRequest request = new StartAttemptRequest(sessionPublicId, true);

        AttemptTaskResponse response = attemptLifecycleService.startAttempt(request, caller);

        assertThat(response.encryptionPublicKey()).isNull();
    }

    @Test
    @DisplayName("startAttempt on STANDARD-pinned attempt preserves all other response fields")
    void startAttemptStandardPinnedPreservesOtherFields() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(studentPublicId, tenantId, List.of("student"));

        PinnedExamSnapshot pinnedSnapshot = createPinnedSnapshot(tenantId, "STANDARD");
        PinnedItem firstItem = pinnedSnapshot.getItems().get(0);

        stubAttemptSave();
        when(snapshotPinService.pin(any(), any(), any())).thenReturn(pinnedSnapshot);

        StartAttemptRequest request = new StartAttemptRequest(sessionPublicId, true);

        AttemptTaskResponse response = attemptLifecycleService.startAttempt(request, caller);

        assertThat(response.attemptPublicId()).isNotNull();
        assertThat(response.attemptStatus()).isEqualTo(AttemptStatus.IN_PROGRESS.name());
        assertThat(response.completed()).isFalse();
        assertThat(response.task()).isNotNull();
        assertThat(response.task().pinnedItemPublicId()).isEqualTo(firstItem.getPublicId());
    }

    @Test
    @DisplayName("startAttempt after the session cutoff is rejected before pinning an attempt")
    void startAttempt_closedSessionIsRejectedBeforePinning() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        SessionService sessionService = org.mockito.Mockito.mock(SessionService.class);
        doThrow(new NotEntitledException()).when(sessionService)
                .lockOpenForAttemptOperation(sessionPublicId, tenantId);
        AttemptLifecycleService cutoffAwareService = new AttemptLifecycleService(
                attemptRepository, pinnedItemRepository, snapshotPinService, cacheService, timerService,
                answerSubmitService, new AttemptMapper(JsonMapper.builder().build()), encryptionKeyProvider,
                submissionDecryptionService, heartbeatService, null, sessionService);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> cutoffAwareService.startAttempt(
                new StartAttemptRequest(sessionPublicId, true), new CurrentUser(studentPublicId, tenantId, List.of("STUDENT")))))
                .isInstanceOf(NotEntitledException.class);

        verify(snapshotPinService, never()).pin(any(), any(), any());
    }

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

    private void stubAttemptSave() {
        when(attemptRepository.save(any(ExamAttempt.class))).thenAnswer(invocation -> {
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
}
