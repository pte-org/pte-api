package com.pte.scoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads what a student submitted, and the exam content it answers, from {@code attempt} when scoring needs it.
 * Scoring never stores a copy of an answer, so the content cannot drift from what the attempt holds.
 */
@Component
public class AnswerContentReader {

    private final AttemptService attemptService;

    public AnswerContentReader(AttemptService attemptService) {
        this.attemptService = attemptService;
    }

    public Optional<SubmittedAnswerView> one(UUID answerPublicId, UUID tenantId) {
        return attemptService.getSubmittedAnswer(answerPublicId, tenantId);
    }

    public Map<UUID, SubmittedAnswerView> forAttempt(UUID attemptPublicId, UUID tenantId) {
        return byAnswer(attemptService.getSubmittedAnswersForAttempt(attemptPublicId, tenantId));
    }

    public Map<UUID, SubmittedAnswerView> forSession(UUID sessionPublicId, UUID tenantId) {
        return byAnswer(attemptService.getSubmittedAnswersForSession(sessionPublicId, tenantId));
    }

    private static Map<UUID, SubmittedAnswerView> byAnswer(java.util.List<SubmittedAnswerView> views) {
        return views.stream().collect(Collectors.toMap(SubmittedAnswerView::answerPublicId, Function.identity(),
                (first, second) -> first));
    }
}
