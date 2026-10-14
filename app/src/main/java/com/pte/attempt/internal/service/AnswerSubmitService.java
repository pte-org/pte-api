package com.pte.attempt.internal.service;

import com.pte.attempt.domain.AttemptAnswer;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.PinnedItem;
import com.pte.attempt.domain.enums.AnswerStatus;
import com.pte.attempt.ResponseConfidence;
import com.pte.attempt.internal.exception.AnswerAlreadySubmittedException;
import com.pte.attempt.internal.repository.AttemptAnswerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a student's answer. Does not itself trigger scoring: scoring only
 * acts on the host's scoring-request command. The answers reach scoring once
 * the attempt is submitted (see {@code AttemptSubmittedEvent}), not per answer,
 * because a student may still change an answer until then.
 */
@Service
public class AnswerSubmitService {

    private final AttemptAnswerRepository attemptAnswerRepository;

    public AnswerSubmitService(AttemptAnswerRepository attemptAnswerRepository) {
        this.attemptAnswerRepository = attemptAnswerRepository;
    }

    /**
     * {@code payload} may be null/blank — a client-side-timeout empty-answer
     * resubmission reuses this exact path rather than a separate auto-expire
     * one, so a task that timed out locally is indistinguishable here from
     * one the student genuinely submitted blank. {@code expired} always
     * persists {@code false} — there is no server-side deadline auto-expiry.
     */
    @Transactional
    public AttemptAnswer submit(ExamAttempt attempt, PinnedItem item, String payload) {
        return submit(attempt, item, payload, null);
    }

    /** Additive confidence-aware path shared by official compatibility and practice execution. */
    @Transactional
    public AttemptAnswer submit(ExamAttempt attempt, PinnedItem item, String payload,
            ResponseConfidence confidence) {
        AttemptAnswer answer = attemptAnswerRepository.findByAttemptIdAndPinnedItemId(attempt.getId(), item.getId())
                .orElseGet(() -> {
                    AttemptAnswer created = new AttemptAnswer();
                    created.setAttempt(attempt);
                    created.setPinnedItem(item);
                    return created;
                });
        answer.setPayload(payload);
        answer.setStatus(AnswerStatus.SUBMITTED);
        answer.setExpired(false);
        answer.setConfidence(confidence);
        try {
            return attemptAnswerRepository.save(answer);
        } catch (DataIntegrityViolationException ex) {
            throw new AnswerAlreadySubmittedException();
        }
    }

    /** Creates an empty answer only when the student skips an item with no saved response. */
    @Transactional
    public AttemptAnswer submitIfAbsent(ExamAttempt attempt, PinnedItem item, String payload) {
        return attemptAnswerRepository.findByAttemptIdAndPinnedItemId(attempt.getId(), item.getId())
                .orElseGet(() -> submit(attempt, item, payload));
    }
}
