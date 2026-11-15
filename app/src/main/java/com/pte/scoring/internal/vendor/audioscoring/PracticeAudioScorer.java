package com.pte.scoring.internal.vendor.audioscoring;

import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.AiScoringRequest;

import java.util.Set;

/**
 * Scores a practice speaking answer from the recording itself. Kept apart from {@code AiAnswerScorer} on purpose:
 * the exam worker never looks these up, so exam answers stay with Azure.
 */
public interface PracticeAudioScorer {

    Set<String> taskTypes();

    /** @throws com.pte.scoring.internal.vendor.AiProviderException when the answer cannot be scored */
    AiScoreResult score(AiScoringRequest request);
}
