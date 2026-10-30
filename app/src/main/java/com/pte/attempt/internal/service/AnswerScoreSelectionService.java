package com.pte.attempt.internal.service;

import com.pte.attempt.dto.response.ScoreSelectionView;
import java.util.List;
import com.pte.attempt.domain.AttemptAnswer;
import com.pte.attempt.internal.repository.AttemptAnswerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Records, on the answer itself, which score source the host chose. Scoring decides what may be chosen; attempt
 * only owns the column, so the choice and the answer can never drift apart.
 */
@Service
public class AnswerScoreSelectionService {

    private final AttemptAnswerRepository answerRepository;

    public AnswerScoreSelectionService(AttemptAnswerRepository answerRepository) {
        this.answerRepository = answerRepository;
    }

    @Transactional(readOnly = true)
    public List<ScoreSelectionView> findForSession(UUID sessionPublicId,
            UUID tenantId) {
        return answerRepository.findSelections(sessionPublicId, tenantId).stream()
                .map(row -> new ScoreSelectionView(row.getAnswerPublicId(),
                        row.getSource(), row.getSelectedAt(), row.getSelectedByPublicId(),
                        row.getEvaluationPublicId()))
                .toList();
    }

    /** Sets the chosen source of every given answer; {@code evaluationByAnswer} names the row each one resolved to. */
    @Transactional
    public int select(Collection<UUID> answerPublicIds, UUID tenantId, String source, UUID actorPublicId,
            Instant selectedAt, Map<UUID, UUID> evaluationByAnswer) {
        if (answerPublicIds == null || answerPublicIds.isEmpty()) {
            return 0;
        }
        var answers = answerRepository.findAllByPublicIdInAndAttempt_TenantId(answerPublicIds, tenantId);
        for (AttemptAnswer answer : answers) {
            answer.setSelectedScoreSource(source);
            answer.setSelectedScoreSourceAt(selectedAt);
            answer.setSelectedScoreSourceByPublicId(actorPublicId);
            answer.setSelectedEvaluationPublicId(evaluationByAnswer.get(answer.getPublicId()));
        }
        answerRepository.saveAll(answers);
        return answers.size();
    }
}
