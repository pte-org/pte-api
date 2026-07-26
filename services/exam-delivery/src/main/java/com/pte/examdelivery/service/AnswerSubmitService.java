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

    @Transactional
    public AttemptAnswer submit(ExamAttempt attempt, PinnedItem item, String payload) {
        return persist(attempt, item, payload, false);
    }

    /** Auto-finalizes the current task as an empty answer when its response window elapsed unanswered. */
    @Transactional
    public AttemptAnswer autoExpire(ExamAttempt attempt, PinnedItem item) {
        return persist(attempt, item, null, true);
    }

    private AttemptAnswer persist(ExamAttempt attempt, PinnedItem item, String payload, boolean expired) {
        AttemptAnswer answer = new AttemptAnswer();
        answer.setAttempt(attempt);
        answer.setPinnedItem(item);
        answer.setPayload(payload);
        answer.setStatus(AnswerStatus.SUBMITTED);
        answer.setExpired(expired);
        AttemptAnswer saved;
        try {
            saved = attemptAnswerRepository.save(answer);
        } catch (DataIntegrityViolationException ex) {
            throw new AnswerAlreadySubmittedException();
        }

        outboxWriter.write(ExamDeliveryConstants.AGGREGATE_ATTEMPT, attempt.getPublicId().toString(),
                ExamDeliveryConstants.EVENT_ANSWER_SUBMITTED,
                new AnswerSubmittedEvent(attempt.getPublicId(), saved.getPublicId(), item.getPublicId(),
                        attempt.getSessionPublicId(), attempt.getTenantId(), expired,
                        item.getTaskType(), payload, item.getCorrectAnswerText(), item.getOptionsJson()),
                attempt.getTenantId());
        return saved;
    }
}
