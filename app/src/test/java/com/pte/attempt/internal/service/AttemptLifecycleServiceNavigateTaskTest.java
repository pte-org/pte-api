package com.pte.attempt.internal.service;

import com.pte.attempt.internal.service.capability.CapabilityNegotiationService;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.internal.config.EncryptionKeyProvider;
import com.pte.attempt.internal.dto.request.NavigateTaskRequest;
import com.pte.attempt.internal.dto.response.AttemptTaskResponse;
import com.pte.attempt.internal.exception.answer.NotCurrentTaskException;
import com.pte.attempt.internal.mapper.AttemptMapper;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.attempt.internal.service.cache.ExamItemView;
import com.pte.session.SessionService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the server-side accept/reject rules of {@link AttemptLifecycleService#navigateTask}:
 * manual navigation is only allowed when both the source and target item are
 * READING/WRITING (see {@code ManualNavigationPolicy}); anything else must be
 * rejected without recording a blank answer or moving the pointer.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttemptLifecycleService.navigateTask")
class AttemptLifecycleServiceNavigateTaskTest {

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

    private AttemptLifecycleService service;
    private CurrentUser caller;
    private UUID attemptPublicId;
    private ExamAttempt attempt;

    @BeforeEach
    void setUp() {
        service = new AttemptLifecycleService(
                attemptRepository, exam.contentService,
                timerService, answerSubmitService, new AttemptMapper(JsonMapper.builder().build()),
                encryptionKeyProvider, submissionDecryptionService, heartbeatService,
                capabilityNegotiationService, sessionService, java.time.Clock.systemUTC());

        UUID studentPublicId = UUID.randomUUID();
        caller = new CurrentUser(studentPublicId, UUID.randomUUID(), List.of("student"));
        attemptPublicId = UUID.randomUUID();

        attempt = new ExamAttempt();
        attempt.setId(1L);
        attempt.setStudentPublicId(studentPublicId);
        attempt.setTenantId(caller.tenantId());
        attempt.begin(java.time.Instant.now());
        attempt.setCurrentOrderIndex(0);

        attempt.setExamMode("OFFICIAL_EXAM");
        attempt.setAnswerIntegrityLevel("STANDARD");

        when(attemptRepository.findByPublicIdAndStudentPublicId(attemptPublicId, studentPublicId))
                .thenReturn(Optional.of(attempt));
        when(attemptRepository.findWithLockById(1L)).thenReturn(Optional.of(attempt));
    }

    private ExamItemView item(int orderIndex, String section) {
        return exam.add(orderIndex, section);
    }

    private NavigateTaskRequest next(ExamItemView from) {
        return new NavigateTaskRequest(from.publicId(), NavigateTaskRequest.Direction.NEXT);
    }

    @Test
    @DisplayName("NEXT within WRITING is accepted: blank answer recorded and timer started on target")
    void navigateTask_nextWithinWriting_isAccepted() {
        ExamItemView from = item(0, "WRITING");
        ExamItemView to = item(1, "WRITING");

        AttemptTaskResponse response = service.navigateTask(attemptPublicId, next(from), caller);

        assertThat(response).isNotNull();
        verify(answerSubmitService).submitIfAbsent(attempt, from, null);
        verify(timerService).startTask(eq(attempt),
                eq(to), any());
        verify(attemptRepository).save(attempt);
    }

    @Test
    @DisplayName("NEXT from WRITING into a SPEAKING item is rejected without side effects")
    void navigateTask_nextFromWritingIntoSpeaking_isRejected() {
        ExamItemView from = item(0, "WRITING");
        item(1, "SPEAKING");

        assertThatThrownBy(() -> service.navigateTask(attemptPublicId, next(from), caller))
                .isInstanceOf(NotCurrentTaskException.class);

        verify(answerSubmitService, never()).submitIfAbsent(any(), any(), any());
        verify(timerService, never()).startTask(any(), any(), any());
        verify(attemptRepository, never()).save(any());
    }

    @Test
    @DisplayName("NEXT from WRITING into a LISTENING item is rejected")
    void navigateTask_nextFromWritingIntoListening_isRejected() {
        ExamItemView from = item(0, "WRITING");
        item(1, "LISTENING");

        assertThatThrownBy(() -> service.navigateTask(attemptPublicId, next(from), caller))
                .isInstanceOf(NotCurrentTaskException.class);

        verify(answerSubmitService, never()).submitIfAbsent(any(), any(), any());
    }

    @Test
    @DisplayName("NEXT from a SPEAKING item into a WRITING item is rejected")
    void navigateTask_nextFromSpeakingIntoWriting_isRejected() {
        ExamItemView from = item(0, "SPEAKING");
        item(1, "WRITING");

        assertThatThrownBy(() -> service.navigateTask(attemptPublicId, next(from), caller))
                .isInstanceOf(NotCurrentTaskException.class);

        verify(answerSubmitService, never()).submitIfAbsent(any(), any(), any());
    }

    @Test
    @DisplayName("NEXT past the last item from a SPEAKING item is rejected and does not complete the attempt")
    void navigateTask_nextPastLastFromSpeaking_isRejected() {
        item(0, "WRITING");
        ExamItemView last = item(1, "SPEAKING");
        attempt.setCurrentOrderIndex(1);

        assertThatThrownBy(() -> service.navigateTask(attemptPublicId, next(last), caller))
                .isInstanceOf(NotCurrentTaskException.class);

        verify(answerSubmitService, never()).submitIfAbsent(any(), any(), any());
        verify(attemptRepository, never()).save(any());
        assertThat(attempt.getStatus()).isEqualTo(com.pte.attempt.domain.enums.AttemptStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("NEXT past the last item from a WRITING item records a blank answer and completes the attempt")
    void navigateTask_nextPastLastFromWriting_completesAttempt() {
        item(0, "WRITING");
        ExamItemView last = item(1, "WRITING");
        attempt.setCurrentOrderIndex(1);

        service.navigateTask(attemptPublicId, next(last), caller);

        verify(answerSubmitService).submitIfAbsent(attempt, last, null);
        assertThat(attempt.getStatus()).isNotEqualTo(com.pte.attempt.domain.enums.AttemptStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("a stale source item (pointer already two ahead) is rejected")
    void navigateTask_staleSource_isRejected() {
        ExamItemView from = item(0, "WRITING");
        item(1, "WRITING");
        item(2, "WRITING");
        attempt.setCurrentOrderIndex(2);

        assertThatThrownBy(() -> service.navigateTask(attemptPublicId, next(from), caller))
                .isInstanceOf(NotCurrentTaskException.class);

        verify(answerSubmitService, never()).submitIfAbsent(any(), any(), any());
    }
}
