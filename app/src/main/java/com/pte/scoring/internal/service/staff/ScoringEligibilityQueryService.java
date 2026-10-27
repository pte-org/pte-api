package com.pte.scoring.internal.service.staff;

import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.dto.response.AiEligibleAttemptView;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Resolves assignment-pool eligibility from the scoring method each answer was given from its exam item. */
@Service
public class ScoringEligibilityQueryService {

    private final AiEvaluationRepository evaluationRepository;

    public ScoringEligibilityQueryService(AiEvaluationRepository evaluationRepository) {
        this.evaluationRepository = evaluationRepository;
    }

    /** A null attempt filter means all submitted attempts in the session; an empty filter means an empty pool. */
    @Transactional(readOnly = true)
    public List<AiEligibleAttemptView> findEligibleAttempts(UUID sessionPublicId, UUID tenantId,
            List<UUID> attemptPublicIds) {
        if (attemptPublicIds != null && attemptPublicIds.isEmpty()) {
            return List.of();
        }
        List<AiEvaluation> answers = attemptPublicIds == null
                ? evaluationRepository.findBySessionPublicIdAndTenantId(sessionPublicId, tenantId)
                : evaluationRepository.findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(
                        sessionPublicId, tenantId, attemptPublicIds);
        Map<UUID, Integer> eligibleCounts = new LinkedHashMap<>();
        for (AiEvaluation answer : answers) {
            if (isAiEligible(answer)) {
                eligibleCounts.merge(answer.getAttemptPublicId(), 1, Integer::sum);
            }
        }
        List<AiEligibleAttemptView> result = new ArrayList<>();
        eligibleCounts.forEach((attemptPublicId, count) -> result.add(new AiEligibleAttemptView(attemptPublicId, count)));
        return result.stream().sorted((left, right) -> left.attemptPublicId().compareTo(right.attemptPublicId())).toList();
    }

    public boolean isAiEligible(AiEvaluation answer) {
        ScoringMethod method = answer.getScoringMethod();
        return method != null && method.isAiScored();
    }
}
