package com.pte.scoring.vendor.stub;

import com.pte.scoring.vendor.AiScoreResult;
import com.pte.scoring.vendor.SpeechScoringClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * PLACEHOLDER — makes NO network call, analyzes NO real audio. Returns a
 * deterministic mid-range score so the pipeline (queue → worker → status
 * transitions → events) is exercisable end-to-end before a real vendor is
 * wired in. The OpenAI-compatible adapter is opt-in through
 * {@code scoring.ai.provider=openai-compatible}; this stub remains the safe
 * default for local development.
 *
 * <p>To replace: implement {@link SpeechScoringClient} against a real
 * audio-capable vendor (e.g. fetch bytes via media's presigned download —
 * add when this class is replaced), remove
 * {@code @Component} here (or gate both behind {@code scoring.ai.provider}),
 * and the worker needs no changes — it only depends on the interface.
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
                "STUB: no real vendor configured yet (phase-09 documented gap).");
    }
}
