package com.pte.attempt.internal.service;

import com.pte.attempt.internal.config.EncryptionKeyProvider;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.internal.dto.request.StartAttemptRequest;
import com.pte.attempt.internal.dto.response.AttemptTaskResponse;
import com.pte.attempt.internal.mapper.AttemptMapper;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.attempt.internal.service.cache.ExamItemView;
import com.pte.shared.security.CurrentUser;
import com.pte.session.SessionService;
import com.pte.session.dto.response.AttemptRetryPolicyResponse;
import com.pte.session.internal.exception.NotEntitledException;
import com.pte.tenancy.internal.exception.TenantSuspendedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Ported from services/exam-delivery's own AttemptServiceTest — same
 * coverage, minus OutboxWriter (no outbox in the monolith; startAttempt is
 * still the ONE call that pins a snapshot). Covers {@code startAttempt}'s
 * {@code encryptionPublicKey} population: non-null only for STRICT
 * attempts.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttemptLifecycleService")
class AttemptLifecycleServiceTest {

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
    private CapabilityNegotiationService capabilityNegotiationService;

    @Mock
    private SessionService sessionService;

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
        lenient().when(sessionService.getAttemptRetryPolicy(any(), any()))
                .thenReturn(new AttemptRetryPolicyResponse(0));
        AttemptMapper attemptMapper = new AttemptMapper(JsonMapper.builder().build());
        attemptLifecycleService = new AttemptLifecycleService(
                attemptRepository,
                exam.contentService,
                timerService,
                answerSubmitService,
                attemptMapper,
                encryptionKeyProvider,
                submissionDecryptionService,
                heartbeatService,
                capabilityNegotiationService,
                sessionService);

    }

    @Test
    @DisplayName("startAttempt on STRICT attempt returns non-null encryptionPublicKey")
    void startAttemptStrictPinnedReturnsPublicKey() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(studentPublicId, tenantId, List.of("student"));

        exam.add(FakeExam.view(0, "READING", "MC_READING_SINGLE", 30, 60));
        exam.stubStart(sessionService, sessionPublicId, studentPublicId, tenantId, "STRICT");

        stubAttemptSave();
        when(encryptionKeyProvider.getPublicKeyBase64()).thenReturn(TEST_PUBLIC_KEY_BASE64);

        StartAttemptRequest request = new StartAttemptRequest(sessionPublicId, true);

        AttemptTaskResponse response = attemptLifecycleService.startAttempt(request, caller);

        assertThat(response.encryptionPublicKey())
            .isNotNull()
            .isNotEmpty();
    }

    @Test
    @DisplayName("startAttempt on STANDARD attempt returns null encryptionPublicKey")
    void startAttemptStandardPinnedReturnsNullPublicKey() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(studentPublicId, tenantId, List.of("student"));

        exam.add(FakeExam.view(0, "READING", "MC_READING_SINGLE", 30, 60));
        exam.stubStart(sessionService, sessionPublicId, studentPublicId, tenantId, "STANDARD");

        stubAttemptSave();

        StartAttemptRequest request = new StartAttemptRequest(sessionPublicId, true);

        AttemptTaskResponse response = attemptLifecycleService.startAttempt(request, caller);

        assertThat(response.encryptionPublicKey()).isNull();
    }

    @Test
    @DisplayName("startAttempt on STANDARD attempt preserves all other response fields")
    void startAttemptStandardPinnedPreservesOtherFields() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(studentPublicId, tenantId, List.of("student"));

        ExamItemView firstItem = exam.add(FakeExam.view(0, "READING", "MC_READING_SINGLE", 30, 60));
        exam.stubStart(sessionService, sessionPublicId, studentPublicId, tenantId, "STANDARD");

        stubAttemptSave();

        StartAttemptRequest request = new StartAttemptRequest(sessionPublicId, true);

        AttemptTaskResponse response = attemptLifecycleService.startAttempt(request, caller);

        assertThat(response.attemptPublicId()).isNotNull();
        assertThat(response.attemptStatus()).isEqualTo(AttemptStatus.IN_PROGRESS.name());
        assertThat(response.completed()).isFalse();
        assertThat(response.task()).isNotNull();
        assertThat(response.task().pinnedItemPublicId()).isEqualTo(firstItem.publicId());
    }

    @Test
    @DisplayName("startAttempt freezes the exam version and the session rules on the attempt")
    void startAttemptCopiesTheExamTermsOntoTheAttempt() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(studentPublicId, tenantId, List.of("student"));
        exam.add(FakeExam.view(0, "READING", "MC_READING_SINGLE", 30, 60));
        UUID versionId = exam.stubStart(sessionService, sessionPublicId, studentPublicId, tenantId, "STRICT");
        when(encryptionKeyProvider.getPublicKeyBase64()).thenReturn(TEST_PUBLIC_KEY_BASE64);
        stubAttemptSave();

        attemptLifecycleService.startAttempt(new StartAttemptRequest(sessionPublicId, true), caller);

        org.mockito.ArgumentCaptor<ExamAttempt> saved = org.mockito.ArgumentCaptor.forClass(ExamAttempt.class);
        verify(attemptRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        ExamAttempt attempt = saved.getAllValues().get(0);
        assertThat(attempt.getExamVersionPublicId()).isEqualTo(versionId);
        assertThat(attempt.getExamDefinitionPublicId()).isNotNull();
        assertThat(attempt.getAnswerIntegrityLevel()).isEqualTo("STRICT");
        assertThat(attempt.getReplayPolicyType()).isEqualTo("UNLIMITED");
        assertThat(attempt.getExamMode()).isEqualTo("OFFICIAL_EXAM");
        assertThat(attempt.getExamEndTime()).isNotNull();
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
                attemptRepository,
                exam.contentService,
                timerService,
                answerSubmitService,
                new AttemptMapper(JsonMapper.builder().build()),
                encryptionKeyProvider,
                submissionDecryptionService,
                heartbeatService,
                capabilityNegotiationService,
                sessionService);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> cutoffAwareService.startAttempt(
                new StartAttemptRequest(sessionPublicId, true), new CurrentUser(studentPublicId, tenantId, List.of("STUDENT")))))
                .isInstanceOf(NotEntitledException.class);

        verify(sessionService, never()).checkEntitlement(any(), any());
    }

    @Test
    @DisplayName("new attempt is blocked for a suspended tenant before pinning")
    void startAttempt_newAttemptIsBlockedWhenTenantIsSuspended() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        doThrow(new TenantSuspendedException()).when(sessionService)
                .assertTenantActiveForNewAttempt(tenantId);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> attemptLifecycleService.startAttempt(
                new StartAttemptRequest(sessionPublicId, true),
                new CurrentUser(studentPublicId, tenantId, List.of("STUDENT")))))
                .isInstanceOf(TenantSuspendedException.class);

        verify(sessionService, never()).checkEntitlement(any(), any());
    }

    @Test
    @DisplayName("resume of an in-progress attempt remains allowed for a suspended tenant")
    void startAttempt_resumeExistingAttemptDoesNotCheckTenantActivity() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(studentPublicId, tenantId, List.of("STUDENT"));
        ExamItemView currentItem = exam.add(FakeExam.view(0, "READING", "MC_READING_SINGLE", 30, 60));
        ExamAttempt existing = new ExamAttempt();
        existing.setPublicId(UUID.randomUUID());
        existing.setSessionPublicId(sessionPublicId);
        existing.setStudentPublicId(studentPublicId);
        existing.setTenantId(tenantId);
        existing.setStatus(AttemptStatus.IN_PROGRESS);
        existing.setAnswerIntegrityLevel("STANDARD");

        when(attemptRepository.findFirstBySessionPublicIdAndStudentPublicIdOrderByAttemptNumberDesc(
                sessionPublicId, studentPublicId)).thenReturn(Optional.of(existing));

        AttemptTaskResponse response = attemptLifecycleService.startAttempt(
                new StartAttemptRequest(sessionPublicId, true), caller);

        assertThat(response.attemptPublicId()).isEqualTo(existing.getPublicId());
        verify(sessionService, never()).assertTenantActiveForNewAttempt(tenantId);
        verify(sessionService, never()).checkEntitlement(any(), any());
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
