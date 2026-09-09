package com.pte.examdelivery.service;

import com.pte.examdelivery.constant.ExamDeliveryConstants;
import com.pte.examdelivery.domain.AttemptAnswer;
import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.domain.PinnedItem;
import com.pte.examdelivery.domain.enums.AnswerStatus;
import com.pte.examdelivery.domain.event.AnswerSubmittedEvent;
import com.pte.examdelivery.domain.exception.AnswerAlreadySubmittedException;
import com.pte.examdelivery.messaging.outbox.OutboxWriter;
import com.pte.examdelivery.repository.AttemptAnswerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a student's answer and writes {@code AnswerSubmitted} to the outbox
 * in the SAME transaction (ADR-002 Transactional Outbox) — never a direct
 * broker publish. Does not itself trigger scoring: scoring only acts on the
 * host's later {@code ScoringRequested} command.
 */
@Service
public class AnswerSubmitService {

    private final AttemptAnswerRepository attemptAnswerRepository;
    private final OutboxWriter outboxWriter;

    public AnswerSubmitService(AttemptAnswerRepository attemptAnswerRepository, OutboxWriter outboxWriter) {
        this.attemptAnswerRepository = attemptAnswerRepository;
        this.outboxWriter = outboxWriter;
    }

    /**
     * {@code payload} may be null/blank — a client-side-timeout empty-answer
     * resubmission (client-side-exam-timer Phase 4, FR-07) reuses this exact
     * path rather than a separate auto-expire one, so a task that timed out
     * locally is indistinguishable here from one the student genuinely
     * submitted blank. The former server-side deadline auto-expiry this
     * service used to also support ({@code autoExpire}, setting
     * {@code AttemptAnswer.expired = true}) was deleted along with the
     * catch-up loop that was its only caller (Phase 5, FR-08) — {@code expired}
     * now always persists {@code false} for every path reaching this method.
     */
    @Transactional
    public AttemptAnswer submit(ExamAttempt attempt, PinnedItem item, String payload) {
        AttemptAnswer answer = new AttemptAnswer();
        answer.setAttempt(attempt);
        answer.setPinnedItem(item);
        answer.setPayload(payload);
        answer.setStatus(AnswerStatus.SUBMITTED);
        answer.setExpired(false);
        AttemptAnswer saved;
        try {
            saved = attemptAnswerRepository.save(answer);
        } catch (DataIntegrityViolationException ex) {
            throw new AnswerAlreadySubmittedException();
        }

        outboxWriter.write(ExamDeliveryConstants.AGGREGATE_ATTEMPT, attempt.getPublicId().toString(),
                ExamDeliveryConstants.EVENT_ANSWER_SUBMITTED,
                new AnswerSubmittedEvent(attempt.getPublicId(), saved.getPublicId(), item.getPublicId(),
                        attempt.getSessionPublicId(), attempt.getTenantId(), false,
                        item.getTaskType(), payload, item.getCorrectAnswerText(), item.getOptionsJson()),
                attempt.getTenantId());
        return saved;
    }
}
