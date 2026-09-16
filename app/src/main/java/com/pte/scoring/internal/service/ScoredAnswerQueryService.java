package com.pte.scoring.internal.service;

import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.dto.response.ScoredAnswerView;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Backs reporting's skill-aggregation pull (Phase 10) — reporting has no repository access of its own into scoring. */
@Service
public class ScoredAnswerQueryService {

    private final ScoringAnswerRepository scoringAnswerRepository;

    public ScoredAnswerQueryService(ScoringAnswerRepository scoringAnswerRepository) {
        this.scoringAnswerRepository = scoringAnswerRepository;
    }

    @Transactional(readOnly = true)
    public List<ScoredAnswerView> findScoredForAttempt(UUID attemptPublicId, UUID tenantId) {
        return scoringAnswerRepository
                .findByAttemptPublicIdAndTenantIdAndStatus(attemptPublicId, tenantId, ScoringAnswerStatus.SCORED)
                .stream()
                .map(answer -> new ScoredAnswerView(answer.getTaskType(), answer.getRawScore()))
                .toList();
    }
}
