package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.dto.response.AiEligibleAttemptView;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Resolves assignment-pool eligibility from each answer's pinned template, never task-type names. */
@Service
public class ScoringEligibilityQueryService {

    private final ScoringAnswerRepository scoringAnswerRepository;
    private final ScoringMethodResolver scoringMethodResolver;

    public ScoringEligibilityQueryService(ScoringAnswerRepository scoringAnswerRepository,
            ScoringMethodResolver scoringMethodResolver) {
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.scoringMethodResolver = scoringMethodResolver;
    }

    /** A null attempt filter means all submitted attempts in the session; an empty filter means an empty pool. */
    @Transactional(readOnly = true)
    public List<AiEligibleAttemptView> findEligibleAttempts(UUID sessionPublicId, UUID tenantId,
            List<UUID> attemptPublicIds) {
        if (attemptPublicIds != null && attemptPublicIds.isEmpty()) {
            return List.of();
        }
        List<ScoringAnswer> answers = attemptPublicIds == null
                ? scoringAnswerRepository.findBySessionPublicIdAndTenantId(sessionPublicId, tenantId)
                : scoringAnswerRepository.findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(
                        sessionPublicId, tenantId, attemptPublicIds);

        Map<UUID, Integer> eligibleCounts = new LinkedHashMap<>();
        for (ScoringAnswer answer : answers) {
            if (isAiEligible(answer)) {
                eligibleCounts.merge(answer.getAttemptPublicId(), 1, Integer::sum);
            }
        }
        List<AiEligibleAttemptView> result = new ArrayList<>();
        eligibleCounts.forEach((attemptPublicId, count) -> result.add(new AiEligibleAttemptView(attemptPublicId, count)));
        return result.stream().sorted((left, right) -> left.attemptPublicId().compareTo(right.attemptPublicId())).toList();
    }

    boolean isAiEligible(ScoringAnswer answer) {
        return scoringMethodResolver.resolve(answer.getScoreTemplatePublicId(), answer.getTaskType())
                .filter(method -> method == ScoringMethod.AI_SPEECH || method == ScoringMethod.AI_TEXT)
                .isPresent();
    }
}
