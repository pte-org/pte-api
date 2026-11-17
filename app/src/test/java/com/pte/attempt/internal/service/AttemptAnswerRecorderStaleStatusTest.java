package com.pte.attempt.internal.service;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.internal.exception.AttemptAlreadyCompleteException;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The attempt is loaded before the transaction waits for the session lock, so when a submit commits in between, the
 * loaded copy still says IN_PROGRESS. The recorder must trust the database's status once the lock is held.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttemptAnswerRecorder: status judged after the lock")
class AttemptAnswerRecorderStaleStatusTest {

    @Mock
    private ExamAttemptRepository attemptRepository;
    @Mock
    private SessionService sessionService;
    @Mock
    private ExamContentService contentService;
    @Mock
    private AnswerSubmitService answerSubmitService;
    @Mock
    private AttemptTaskProgression progression;

    private AttemptAnswerRecorder recorder;
    private ExamAttempt staleAttempt;
    private final UUID itemId = UUID.randomUUID();
    private final CurrentUser caller = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("STUDENT"));

    @BeforeEach
    void setUp() {
        AttemptAccess access = new AttemptAccess(attemptRepository, sessionService, com.pte.TestClocks.fixed());
        recorder = new AttemptAnswerRecorder(contentService, answerSubmitService, access, progression);

        // What findOwned loaded before the wait: still in progress, and findWithLockById hands the same instance back.
        staleAttempt = new ExamAttempt();
        staleAttempt.setId(7L);
        staleAttempt.begin(Instant.now());
        when(attemptRepository.findWithLockById(7L)).thenReturn(Optional.of(staleAttempt));
    }

    @Test
    @DisplayName("a draft save is refused when the attempt was submitted while it waited for the lock")
    void saveWithoutAdvanceRefusedAfterConcurrentSubmit() {
        when(attemptRepository.findStatusById(7L)).thenReturn(Optional.of(AttemptStatus.SUBMITTED));

        assertThatThrownBy(() -> recorder.saveAnswerWithoutAdvance(staleAttempt, itemId, "late text", null, caller))
                .isInstanceOf(AttemptAlreadyCompleteException.class);

        verify(answerSubmitService, never()).submit(any(), any(), any());
        verify(answerSubmitService, never()).submit(any(), any(), any(), any());
    }

    @Test
    @DisplayName("an answer that advances the task is refused for the same reason")
    void processAnswerRefusedAfterConcurrentSubmit() {
        when(attemptRepository.findStatusById(7L)).thenReturn(Optional.of(AttemptStatus.SUBMITTED));

        assertThatThrownBy(() -> recorder.processAnswer(staleAttempt, itemId, "late text", null, caller))
                .isInstanceOf(AttemptAlreadyCompleteException.class);

        verify(answerSubmitService, never()).submit(any(), any(), any());
        verify(progression, never()).advanceAfterCurrent(any(), any());
    }

    @Test
    @DisplayName("an attempt still in progress in the database saves its draft as before")
    void saveWithoutAdvanceStillWorksWhileInProgress() {
        when(attemptRepository.findStatusById(7L)).thenReturn(Optional.of(AttemptStatus.IN_PROGRESS));
        var item = FakeExam.withId(FakeExam.view(0, "WRITING", "WRITE_ESSAY", 0, 60), itemId);
        when(contentService.item(staleAttempt, itemId)).thenReturn(Optional.of(item));

        recorder.saveAnswerWithoutAdvance(staleAttempt, itemId, "draft", null, caller);

        verify(answerSubmitService).submit(staleAttempt, item, "draft");
    }
}
