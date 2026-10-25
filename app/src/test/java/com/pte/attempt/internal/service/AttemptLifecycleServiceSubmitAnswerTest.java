package com.pte.attempt.internal.service;

import com.pte.attempt.internal.service.capability.CapabilityNegotiationService;
import com.pte.attempt.internal.config.EncryptionKeyProvider;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.internal.dto.request.SubmitAnswerRequest;
import com.pte.attempt.internal.dto.response.AttemptTaskResponse;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Ported from services/exam-delivery's own AttemptServiceSubmitAnswerTest —
 * same coverage, new package. Covers {@code submitAnswer} (STANDARD path):
 * a blank/null payload is accepted (no rejection) and processed as a normal,
 * if content-less, answer.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttemptLifecycleService.submitAnswer")
class AttemptLifecycleServiceSubmitAnswerTest {

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
    private UUID studentPublicId;
    private CurrentUser caller;
    private UUID attemptPublicId;
    private UUID pinnedItemPublicId;
    private ExamAttempt attempt;
    private ExamItemView item;

    @BeforeEach
    void setUp() {
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
                sessionService, java.time.Clock.systemUTC());

        studentPublicId = UUID.randomUUID();
        caller = new CurrentUser(studentPublicId, UUID.randomUUID(), List.of("student"));
        attemptPublicId = UUID.randomUUID();
        pinnedItemPublicId = UUID.randomUUID();

        attempt = new ExamAttempt();
        attempt.setId(1L);
        attempt.setStudentPublicId(studentPublicId);
        attempt.setTenantId(caller.tenantId());
        attempt.begin(java.time.Instant.now());
        attempt.setCurrentOrderIndex(0);

        attempt.setAnswerIntegrityLevel("STANDARD");

        item = exam.add(FakeExam.withId(FakeExam.view(0, "WRITING", "WRITE_ESSAY", 0, 60), pinnedItemPublicId));
        // 2 items total -> submitting item 0 advances to item 1, doesn't complete.
        exam.add(1, "WRITING");

        when(attemptRepository.findByPublicIdAndStudentPublicId(attemptPublicId, studentPublicId))
            .thenReturn(Optional.of(attempt));
        when(attemptRepository.findWithLockById(1L)).thenReturn(Optional.of(attempt));
    }

    @Test
    @DisplayName("null payload is accepted as a normal (content-less) answer, not a 400")
    void submitAnswer_nullPayload_acceptedAndProcessed() {
        assertAcceptedAndProcessed(null);
    }

    @Test
    @DisplayName("empty-string payload is accepted as a normal (content-less) answer, not a 400")
    void submitAnswer_emptyPayload_acceptedAndProcessed() {
        assertAcceptedAndProcessed("");
    }

    @Test
    @DisplayName("whitespace-only payload is accepted as a normal (content-less) answer, not a 400")
    void submitAnswer_whitespaceOnlyPayload_acceptedAndProcessed() {
        assertAcceptedAndProcessed("   ");
    }

    private void assertAcceptedAndProcessed(String payload) {
        SubmitAnswerRequest request = new SubmitAnswerRequest(pinnedItemPublicId, payload);

        AttemptTaskResponse response = attemptLifecycleService.submitAnswer(attemptPublicId, request, caller);

        assertThat(response.completed()).isFalse();
        org.mockito.Mockito.verify(answerSubmitService).submit(eq(attempt), eq(item), eq(payload));
    }

    @Test
    @DisplayName("a normal non-blank payload is still accepted exactly as before (unaffected)")
    void submitAnswer_nonBlankPayload_stillAccepted() {
        SubmitAnswerRequest request = new SubmitAnswerRequest(pinnedItemPublicId, "some essay text");

        AttemptTaskResponse response = attemptLifecycleService.submitAnswer(attemptPublicId, request, caller);

        assertThat(response.completed()).isFalse();
        org.mockito.Mockito.verify(answerSubmitService).submit(attempt, item, "some essay text");
    }

    @Test
    @DisplayName("an answer submitted long after the attempt started is still accepted — no server-side deadline gates acceptance")
    void submitAnswer_longAfterAttemptStarted_stillAccepted() {
        attempt.setStartedAt(attempt.getStartedAt().minusSeconds(24 * 60 * 60));

        SubmitAnswerRequest request = new SubmitAnswerRequest(pinnedItemPublicId, "late but valid essay text");
        AttemptTaskResponse response = attemptLifecycleService.submitAnswer(attemptPublicId, request, caller);

        assertThat(response.completed()).isFalse();
        org.mockito.Mockito.verify(answerSubmitService).submit(attempt, item, "late but valid essay text");
    }
}
