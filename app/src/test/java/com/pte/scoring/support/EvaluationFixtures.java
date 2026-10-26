package com.pte.scoring.support;

import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.ScoringMethod;

import java.util.UUID;

/** Builds scoring-side evaluations with fresh identities, for tests that only care about status and scores. */
public final class EvaluationFixtures {

    private EvaluationFixtures() {
    }

    public static AiEvaluation ai(String taskType, ScoringMethod method) {
        return ai(UUID.randomUUID(), UUID.randomUUID(), taskType, method);
    }

    public static AiEvaluation ai(UUID tenantId, UUID sessionPublicId, String taskType, ScoringMethod method) {
        return new AiEvaluation(tenantId, sessionPublicId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), taskType, "SPEAKING", method);
    }

    public static AiEvaluation ai(UUID tenantId, UUID sessionPublicId, UUID attemptPublicId, UUID answerPublicId,
            String taskType, ScoringMethod method) {
        return new AiEvaluation(tenantId, sessionPublicId, attemptPublicId, answerPublicId, UUID.randomUUID(),
                UUID.randomUUID(), taskType, "SPEAKING", method);
    }

    public static AiEvaluation ai(UUID tenantId, UUID sessionPublicId, UUID attemptPublicId, UUID answerPublicId,
            String taskType, ScoringMethod method, String section) {
        return new AiEvaluation(tenantId, sessionPublicId, attemptPublicId, answerPublicId, UUID.randomUUID(),
                UUID.randomUUID(), taskType, section, method);
    }
}
