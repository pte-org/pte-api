package com.pte.scoring.internal.vendor;

import java.util.Set;

/**
 * Scores the answers of the task types it names. The worker looks the scorer
 * up by task type; a task type with no scorer falls back to the generic
 * speech or essay client.
 */
public interface AiAnswerScorer {

    Set<String> taskTypes();

    /** @throws AiProviderException when the answer cannot be scored; the worker records the reason */
    AiScoreResult score(AiScoringRequest request);
}
