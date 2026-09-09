package com.pte.examdelivery.service;

import com.pte.common.security.CurrentUser;
import com.pte.examdelivery.config.EncryptionKeyProvider;
import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.domain.PinnedExamSnapshot;
import com.pte.examdelivery.domain.PinnedItem;
import com.pte.examdelivery.dto.request.SubmitAnswerRequest;
import com.pte.examdelivery.dto.response.AttemptTaskResponse;
import com.pte.examdelivery.mapper.AttemptMapper;
import com.pte.examdelivery.messaging.outbox.OutboxWriter;
import com.pte.examdelivery.repository.ExamAttemptRepository;
import com.pte.examdelivery.repository.PinnedItemRepository;
import com.pte.examdelivery.service.cache.PinnedSnapshotCacheService;

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
 * Covers {@code AttemptService.submitAnswer} (STANDARD path) — no dedicated
 * happy-path test existed before client-side-exam-timer Phase 2 (a pre-existing
 * gap; {@code AttemptServicePhase2Test} only covers the STRICT/STANDARD routing
 * rejection). Added specifically to verify FR-07: a blank/null payload is
 * accepted (no 400/rejection) and processed as a normal, if content-less,
 * answer — replacing the removed server-side auto-expire with a client-driven
 * "timeout, nothing answered" resubmission.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttemptService.submitAnswer (client-side-exam-timer Phase 2, FR-07)")
class AttemptServiceSubmitAnswerTest {

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
    private OutboxWriter outboxWriter;

    @Mock
    private EncryptionKeyProvider encryptionKeyProvider;

    @Mock
    private SubmissionDecryptionService submissionDecryptionService;

    @Mock
    private HeartbeatService heartbeatService;

    private AttemptService attemptService;
    private UUID studentPublicId;
    private CurrentUser caller;
    private UUID attemptPublicId;
    private UUID pinnedItemPublicId;
    private ExamAttempt attempt;
    private PinnedItem item;

    @BeforeEach
    void setUp() {
        AttemptMapper attemptMapper = new AttemptMapper(JsonMapper.builder().build());
        attemptService = new AttemptService(
            attemptRepository,
            pinnedItemRepository,
            snapshotPinService,
            cacheService,
            timerService,
            answerSubmitService,
            attemptMapper,
            outboxWriter,
            encryptionKeyProvider,
            submissionDecryptionService,
            heartbeatService
        );

        studentPublicId = UUID.randomUUID();
        caller = new CurrentUser(studentPublicId, UUID.randomUUID(), List.of("student"));
        attemptPublicId = UUID.randomUUID();
        pinnedItemPublicId = UUID.randomUUID();

        attempt = new ExamAttempt();
        attempt.setId(1L);
        attempt.begin();
        attempt.setCurrentOrderIndex(0);

        PinnedExamSnapshot snapshot = new PinnedExamSnapshot();
        snapshot.setId(1L);
        snapshot.setAnswerIntegrityLevel("STANDARD");
        attempt.setPinnedSnapshot(snapshot);

        item = new PinnedItem();
        item.setId(1L);
        item.setPublicId(pinnedItemPublicId);
        item.setOrderIndex(0);
        item.setSection("WRITING");
        item.setTaskType("WRITE_ESSAY");
        item.setPinnedSnapshot(snapshot);

        when(attemptRepository.findWithPinnedByPublicIdAndStudentPublicId(attemptPublicId, studentPublicId))
            .thenReturn(Optional.of(attempt));
        when(attemptRepository.findWithLockById(1L)).thenReturn(Optional.of(attempt));
        when(pinnedItemRepository.findByPinnedSnapshotIdAndOrderIndex(1L, 0)).thenReturn(Optional.of(item));
        // 2 pinned items total -> submitting item 0 advances to item 1, doesn't complete.
        when(pinnedItemRepository.countByPinnedSnapshotId(1L)).thenReturn(2L);
        PinnedItem secondItem = new PinnedItem();
        secondItem.setId(2L);
        secondItem.setPublicId(UUID.randomUUID());
        secondItem.setOrderIndex(1);
        secondItem.setSection("WRITING");
        secondItem.setTaskType("WRITE_ESSAY");
        secondItem.setPinnedSnapshot(snapshot);
        when(pinnedItemRepository.findByPinnedSnapshotIdAndOrderIndex(1L, 1)).thenReturn(Optional.of(secondItem));
        snapshot.addItem(item);
        snapshot.addItem(secondItem);
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

        AttemptTaskResponse response = attemptService.submitAnswer(attemptPublicId, request, caller);

        assertThat(response.completed()).isFalse();
        org.mockito.Mockito.verify(answerSubmitService).submit(eq(attempt), eq(item), eq(payload));
    }

    @Test
    @DisplayName("a normal non-blank payload is still accepted exactly as before (unaffected)")
    void submitAnswer_nonBlankPayload_stillAccepted() {
        SubmitAnswerRequest request = new SubmitAnswerRequest(pinnedItemPublicId, "some essay text");

        AttemptTaskResponse response = attemptService.submitAnswer(attemptPublicId, request, caller);

        assertThat(response.completed()).isFalse();
        org.mockito.Mockito.verify(answerSubmitService).submit(attempt, item, "some essay text");
    }

    /**
     * client-side-exam-timer Phase 5 Success Criteria: "a test submits an answer
     * well past the old deadline window and it is still accepted, proving wall-clock
     * elapsed time no longer gates acceptance." The old {@code responseDeadline}
     * concept no longer exists to even construct here — this test proves the
     * absence positively, by starting the attempt far enough in the past that the
     * former grace-window check ({@code responseDeadline + 15s}) would certainly
     * have expired many times over, and confirming acceptance is still unconditional.
     */
    @Test
    @DisplayName("an answer submitted long after the attempt started (far past any former deadline) is still accepted")
    void submitAnswer_longAfterAttemptStarted_stillAccepted() {
        attempt.setStartedAt(attempt.getStartedAt().minusSeconds(24 * 60 * 60)); // started 24h ago

        SubmitAnswerRequest request = new SubmitAnswerRequest(pinnedItemPublicId, "late but valid essay text");
        AttemptTaskResponse response = attemptService.submitAnswer(attemptPublicId, request, caller);

        assertThat(response.completed()).isFalse();
        org.mockito.Mockito.verify(answerSubmitService).submit(attempt, item, "late but valid essay text");
    }
}
