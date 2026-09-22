package com.pte.attempt.internal.service;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.PinnedExamSnapshot;
import com.pte.attempt.domain.PinnedItem;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.internal.config.EncryptionKeyProvider;
import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.attempt.internal.dto.request.ClientCapabilityManifest;
import com.pte.attempt.internal.dto.request.StartAttemptRequest;
import com.pte.attempt.internal.exception.ExamCapabilityException;
import com.pte.attempt.internal.mapper.AttemptMapper;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.attempt.internal.repository.PinnedItemRepository;
import com.pte.attempt.internal.service.cache.PinnedSnapshotCacheService;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttemptLifecycleCapabilityTest {

    private final UUID sessionId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final CurrentUser caller = new CurrentUser(studentId, tenantId, List.of("STUDENT"));

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
    @Mock
    private CapabilityNegotiationService capabilityNegotiationService;

    private AttemptLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new AttemptLifecycleService(attemptRepository, pinnedItemRepository, snapshotPinService,
                cacheService, timerService, answerSubmitService, new AttemptMapper(JsonMapper.builder().build()),
                encryptionKeyProvider, submissionDecryptionService, heartbeatService, capabilityNegotiationService);

    }

    @Test
    void start_repeatsAuthoritativeCheckAndStoresNormalizedFingerprint() {
        PinnedExamSnapshot pinned = pinnedSnapshot();
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
        when(timerService.resolveEffectivePrepSeconds(any())).thenReturn(10);
        when(timerService.resolveEffectiveResponseSeconds(any(), any(), anyList())).thenReturn(20);
        when(attemptRepository.findWithPinnedBySessionPublicIdAndStudentPublicId(sessionId, studentId))
                .thenReturn(Optional.empty());
        when(capabilityNegotiationService.authorizeStart(sessionId, studentId, manifest(), caller))
                .thenReturn("AUDIO_RECORDING@1");
        when(snapshotPinService.pin(any(), eq(sessionId), eq(studentId))).thenReturn(pinned);

        var response = service.startAttempt(new StartAttemptRequest(sessionId, true, manifest()), caller);

        assertThat(response.completed()).isFalse();
        ArgumentCaptor<ExamAttempt> captor = ArgumentCaptor.forClass(ExamAttempt.class);
        verify(attemptRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getCapabilityFingerprint()).isEqualTo("AUDIO_RECORDING@1");
        verify(capabilityNegotiationService).verifyPinnedForStart(eq(pinned), eq("AUDIO_RECORDING@1"), eq(caller));
    }

    @Test
    void existingStart_checksCapabilitiesAfterOwnershipBeforeResume() {
        ExamAttempt existing = inProgressAttempt();
        when(attemptRepository.findWithPinnedBySessionPublicIdAndStudentPublicId(sessionId, studentId))
                .thenReturn(Optional.of(existing));
        ExamCapabilityException failure = new ExamCapabilityException(
                AttemptConstants.EXAM_REQUIRES_APP_UPDATE, List.of("AUDIO_RECORDING"));
        doThrow(failure).when(capabilityNegotiationService).authorizeExisting(existing, null, caller);

        assertThatThrownBy(() -> service.startAttempt(new StartAttemptRequest(sessionId, true), caller))
                .isSameAs(failure);
        verify(capabilityNegotiationService).authorizeExisting(existing, null, caller);
        verify(pinnedItemRepository, never()).countByPinnedSnapshotId(any());
    }

    @Test
    void nextTask_checksStoredCapabilitiesBeforeReadingOrReturningPinnedContent() {
        ExamAttempt existing = inProgressAttempt();
        when(attemptRepository.findWithPinnedByPublicIdAndStudentPublicId(existing.getPublicId(), studentId))
                .thenReturn(Optional.of(existing));
        ExamCapabilityException failure = new ExamCapabilityException(
                AttemptConstants.EXAM_REQUIRES_APP_UPDATE, List.of("AUDIO_RECORDING"));
        doThrow(failure).when(capabilityNegotiationService).assertStoredCapabilities(existing, caller);

        assertThatThrownBy(() -> service.getNextTask(existing.getPublicId(), caller))
                .isSameAs(failure);
        verify(capabilityNegotiationService).assertStoredCapabilities(existing, caller);
        verify(pinnedItemRepository, never()).countByPinnedSnapshotId(any());
    }

    private ClientCapabilityManifest manifest() {
        return new ClientCapabilityManifest(List.of("AUDIO_RECORDING"));
    }

    private ExamAttempt inProgressAttempt() {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(1L);
        attempt.setPublicId(UUID.randomUUID());
        attempt.setStudentPublicId(studentId);
        attempt.setTenantId(tenantId);
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        attempt.setPinnedSnapshot(pinnedSnapshot());
        return attempt;
    }

    private PinnedExamSnapshot pinnedSnapshot() {
        PinnedExamSnapshot snapshot = new PinnedExamSnapshot();
        snapshot.setId(10L);
        snapshot.setPublicId(UUID.randomUUID());
        snapshot.setSourceSnapshotPublicId(UUID.randomUUID());
        snapshot.setSourceSessionPublicId(sessionId);
        snapshot.setTenantId(tenantId);
        snapshot.setReplayPolicyType("UNLIMITED");
        snapshot.setAnswerIntegrityLevel("STANDARD");
        snapshot.setDeviceCheckRequired(false);
        snapshot.setProctorRequired(false);

        PinnedItem item = new PinnedItem();
        item.setId(11L);
        item.setPublicId(UUID.randomUUID());
        item.setOrderIndex(0);
        item.setSection("SPEAKING");
        item.setTaskType("READ_ALOUD");
        item.setTaskTypeCode("READ_ALOUD");
        item.setTitle("Read aloud");
        item.setPromptText("Prompt");
        item.setPrepSeconds(10);
        item.setResponseSeconds(20);
        item.pinRuntimeProfile(TaskRuntimeProfileRegistry.descriptorFor("READ_ALOUD"),
                "CANONICAL_V1", "RESOLVED_CANONICAL");
        snapshot.addItem(item);
        return snapshot;
    }
}
