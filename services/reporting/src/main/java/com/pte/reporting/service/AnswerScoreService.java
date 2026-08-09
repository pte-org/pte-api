package com.pte.reporting.service;

import com.pte.reporting.messaging.consumer.dto.AnswerScoredEvent;
import com.pte.reporting.repository.AnswerProjectionRepository;
import org.springframework.stereotype.Service;

/**
 * Upsert logic for reporting's projection of scoring's {@code AnswerScored} —
 * shared by the steady-state {@code AnswerScoredConsumer} and the read-model
 * rebuild orchestration (rabbitmq-outbox-migration Phase 9).
 */
@Service
public class AnswerScoreService {

    private final AnswerProjectionRepository answerProjectionRepository;

    public AnswerScoreService(AnswerProjectionRepository answerProjectionRepository) {
        this.answerProjectionRepository = answerProjectionRepository;
    }

    public void applyScore(AnswerScoredEvent event) {
        answerProjectionRepository.findByAnswerPublicId(event.answerPublicId())
                .ifPresent(answer -> {
                    answer.applyScore(event.rawScore());
                    answerProjectionRepository.save(answer);
                });
    }
}
