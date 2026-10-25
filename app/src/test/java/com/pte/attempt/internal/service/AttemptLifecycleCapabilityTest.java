package com.pte.attempt.internal.service;

import com.pte.attempt.internal.service.capability.CapabilityNegotiationService;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.internal.config.EncryptionKeyProvider;
import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.attempt.internal.dto.request.ClientCapabilityManifest;
import com.pte.attempt.internal.dto.request.StartAttemptRequest;
import com.pte.attempt.internal.exception.AttemptNotFoundException;
import com.pte.attempt.internal.exception.ExamCapabilityException;
import com.pte.attempt.internal.mapper.AttemptMapper;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.session.SessionService;
import com.pte.session.dto.response.AttemptRetryPolicyResponse;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttemptLifecycleCapabilityTest {

    private final UUID sessionId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final CurrentUser caller = new CurrentUser(studentId, tenantId, List.of("STUDENT"));

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
    private SessionService sessionService;
    @Mock
    private CapabilityNegotiationService capabilityNegotiationService;

    private AttemptLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new AttemptLifecycleService(
                attemptRepository,
                exam.contentService,
                timerService,
                answerSubmitService,
                new AttemptMapper(JsonMapper.builder().build()),
                encryptionKeyProvider,
                submissionDecryptionService,
                heartbeatService,
                capabilityNegotiationService,
                sessionService, java.time.Clock.systemUTC());
        lenient().when(sessionService.getAttemptRetryPolicy(any(), any()))
                .thenReturn(new AttemptRetryPolicyResponse(0));
    }

    @Test
    void start_repeatsAuthoritativeCheckAndStoresNormalizedFingerprint() {
        exam.add(FakeExam.view(0, "SPEAKING", "READ_ALOUD", 10, 20));
        UUID versionId = exam.stubStart(sessionService, sessionId, studentId, tenantId, "STANDARD");
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
        when(attemptRepository.findFirstBySessionPublicIdAndStudentPublicIdOrderByAttemptNumberDesc(
                sessionId, studentId)).thenReturn(Optional.empty());
        when(capabilityNegotiationService.authorizeStart(sessionId, studentId, manifest(), caller))
                .thenReturn("AUDIO_RECORDING@1");

        var response = service.startAttempt(new StartAttemptRequest(sessionId, true, manifest()), caller);

        assertThat(response.completed()).isFalse();
        ArgumentCaptor<ExamAttempt> captor = ArgumentCaptor.forClass(ExamAttempt.class);
        verify(attemptRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getCapabilityFingerprint()).isEqualTo("AUDIO_RECORDING@1");
        verify(capabilityNegotiationService).verifyForStart(eq(exam.items), eq(versionId), eq("AUDIO_RECORDING@1"), eq(caller));
    }

    @Test
    void existingStart_checksCapabilitiesAfterOwnershipBeforeResume() {
        ExamAttempt existing = inProgressAttempt();
        when(attemptRepository.findFirstBySessionPublicIdAndStudentPublicIdOrderByAttemptNumberDesc(
                sessionId, studentId)).thenReturn(Optional.of(existing));
        ExamCapabilityException failure = new ExamCapabilityException(
                AttemptConstants.EXAM_REQUIRES_APP_UPDATE, List.of("AUDIO_RECORDING"));
        doThrow(failure).when(capabilityNegotiationService).authorizeExisting(existing, null, caller);

        assertThatThrownBy(() -> service.startAttempt(new StartAttemptRequest(sessionId, true), caller))
                .isSameAs(failure);
        verify(capabilityNegotiationService).authorizeExisting(existing, null, caller);
        verifyNoInteractions(timerService);
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void existingStart_wrongTenant_rejectsBeforeCapabilityCheckOrResume() {
        ExamAttempt existing = inProgressAttempt();
        existing.setTenantId(UUID.randomUUID());
        when(attemptRepository.findFirstBySessionPublicIdAndStudentPublicIdOrderByAttemptNumberDesc(sessionId, studentId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.startAttempt(new StartAttemptRequest(sessionId, true), caller))
                .isInstanceOf(AttemptNotFoundException.class);

        verifyNoInteractions(capabilityNegotiationService, timerService);
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void nextTask_checksStoredCapabilitiesBeforeReadingOrReturningExamContent() {
        ExamAttempt existing = inProgressAttempt();
        when(attemptRepository.findByPublicIdAndStudentPublicId(existing.getPublicId(), studentId))
                .thenReturn(Optional.of(existing));
        ExamCapabilityException failure = new ExamCapabilityException(
                AttemptConstants.EXAM_REQUIRES_APP_UPDATE, List.of("AUDIO_RECORDING"));
        doThrow(failure).when(capabilityNegotiationService).assertStoredCapabilities(existing, caller);

        assertThatThrownBy(() -> service.getNextTask(existing.getPublicId(), caller))
                .isSameAs(failure);
        verify(capabilityNegotiationService).assertStoredCapabilities(existing, caller);
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
        return attempt;
    }
}
