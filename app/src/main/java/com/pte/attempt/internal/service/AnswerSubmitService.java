package com.pte.attempt.internal.service;

import com.pte.attempt.domain.AttemptAnswer;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.PinnedItem;
import com.pte.attempt.domain.enums.AnswerStatus;
import com.pte.attempt.internal.exception.AnswerAlreadySubmittedException;
import com.pte.attempt.internal.repository.AttemptAnswerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a student's answer. Does not itself trigger scoring: scoring
 * (Phase 08) only acts on the host's later scoring-request command, and will
 * receive this data through an application event/work handoff at that point
 * — not implemented yet since scoring doesn't exist in the monolith until
 * then (same deferral precedent as Phase 06's host-gated commands).
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
        AttemptAnswer answer = new AttemptAnswer();
        answer.setAttempt(attempt);
        answer.setPinnedItem(item);
        answer.setPayload(payload);
        answer.setStatus(AnswerStatus.SUBMITTED);
        answer.setExpired(false);
        try {
            return attemptAnswerRepository.save(answer);
        } catch (DataIntegrityViolationException ex) {
            throw new AnswerAlreadySubmittedException();
        }
    }
}
