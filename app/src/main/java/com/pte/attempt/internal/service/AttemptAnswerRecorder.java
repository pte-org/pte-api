package com.pte.attempt.internal.service;

import com.pte.attempt.ResponseConfidence;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.internal.dto.response.AttemptTaskResponse;
import com.pte.attempt.internal.exception.AttemptAlreadyCompleteException;
import com.pte.attempt.internal.exception.answer.AnswerIntegrityLevelMismatchException;
import com.pte.attempt.internal.exception.answer.NotCurrentTaskException;
import com.pte.attempt.internal.service.cache.ExamItemView;
import com.pte.shared.security.CurrentUser;

import java.util.UUID;

/**
 * Persists a plaintext answer (STANDARD, or STRICT after decryption) either
 * advancing past the current task or as a draft that leaves the pointer alone.
 */
class AttemptAnswerRecorder {

    private final ExamContentService contentService;
    private final AnswerSubmitService answerSubmitService;
    private final AttemptAccess access;
    private final AttemptTaskProgression progression;

    AttemptAnswerRecorder(ExamContentService contentService, AnswerSubmitService answerSubmitService,
            AttemptAccess access, AttemptTaskProgression progression) {
        this.contentService = contentService;
        this.answerSubmitService = answerSubmitService;
        this.access = access;
        this.progression = progression;
    }

    /** Request shape (plain vs. encrypted) is server-decided by the attempt level, never client-chosen. */
    void requireIntegrityLevel(ExamAttempt attempt, String expectedLevel) {
        if (!expectedLevel.equals(attempt.getAnswerIntegrityLevel())) {
            throw new AnswerIntegrityLevelMismatchException();
        }
    }

    AttemptTaskResponse processAnswer(ExamAttempt attempt, UUID pinnedItemPublicId, String payload,
                                       ResponseConfidence confidence,
                                       CurrentUser caller) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AttemptAlreadyCompleteException();
        }
        // Same lock as playAudio: currentOrderIndex/playCount now live on the
        // same @Version-guarded ExamAttempt row playAudio locks. Without also
        // locking here, a concurrent playAudio commit between this method's
        // unlocked read and its eventual flush bumps `version` out from under
        // this transaction, and Hibernate's dirty-check UPDATE (WHERE id=? AND
        // version=<stale>) matches zero rows -> ObjectOptimisticLockingFailureException,
        // uncaught, surfaces as a bare 500. Locking here too means this
        // transaction only ever reads the version as of its own lock
        // acquisition, never a stale one.
        attempt = access.lockAttempt(attempt);
        ExamItemView currentItem = contentService.itemAt(attempt, attempt.getCurrentOrderIndex());
        if (!currentItem.publicId().equals(pinnedItemPublicId)) {
            throw new NotCurrentTaskException();
        }

        persistAnswer(attempt, currentItem, payload, confidence);
        return progression.advanceAfterCurrent(attempt, caller);
    }

    AttemptTaskResponse saveAnswerWithoutAdvance(ExamAttempt attempt, UUID pinnedItemPublicId,
                                                  String payload,
                                                  ResponseConfidence confidence,
                                                  CurrentUser caller) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AttemptAlreadyCompleteException();
        }
        attempt = access.lockAttempt(attempt);
        ExamItemView answerItem = contentService.item(attempt, pinnedItemPublicId)
                .orElseThrow(NotCurrentTaskException::new);
        // Saving an answer does not move the task pointer. Allow background
        // sync for every section; mode restrictions apply only to navigation.
        persistAnswer(attempt, answerItem, payload, confidence);
        return progression.advanceUntilLiveOrComplete(attempt, caller);
    }

    private void persistAnswer(ExamAttempt attempt, ExamItemView item, String payload,
            ResponseConfidence confidence) {
        if (confidence == null) {
            // Preserve the established official call shape and behavior for
            // clients that do not send the additive practice field.
            answerSubmitService.submit(attempt, item, payload);
        } else {
            answerSubmitService.submit(attempt, item, payload, confidence);
        }
    }
}
