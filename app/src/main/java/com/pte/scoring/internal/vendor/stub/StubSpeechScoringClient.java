package com.pte.scoring.internal.vendor.stub;

import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.SpeechScoringClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * PLACEHOLDER — makes NO network call, analyzes NO real audio. Returns a
 * deterministic mid-range score so the pipeline (queue → worker → status
 * transitions) is exercisable end-to-end before a real vendor is wired in.
 *
 * <p>To replace: implement {@link SpeechScoringClient} against a real
 * audio-capable vendor, remove {@code @Component} here (or gate both behind
 * {@code scoring.ai.provider}) — the worker needs no changes, it only depends
 * on the interface.
 */
@Component
@ConditionalOnProperty(
        name = "scoring.ai.provider", havingValue = "stub", matchIfMissing = true)
public class StubSpeechScoringClient implements SpeechScoringClient {

    private static final int PLACEHOLDER_SCORE = 65;

    @Override
    public AiScoreResult score(String audioMediaPublicId, String referenceText, UUID tenantId) {
        return new AiScoreResult(PLACEHOLDER_SCORE,
                Map.of("ORAL_FLUENCY", PLACEHOLDER_SCORE, "PRONUNCIATION", PLACEHOLDER_SCORE),
                "STUB: no real vendor configured yet.");
    }
}
