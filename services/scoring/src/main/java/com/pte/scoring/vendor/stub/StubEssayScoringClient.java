package com.pte.scoring.vendor.stub;

import com.pte.scoring.vendor.AiScoreResult;
import com.pte.scoring.vendor.EssayScoringClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * PLACEHOLDER — makes NO network call. A real adapter is a natural fit here
 * (LLM-as-judge with a rubric prompt over plain essay text — no audio/ASR
 * needed). The OpenAI-compatible adapter is opt-in through
 * {@code scoring.ai.provider=openai-compatible}; the stub remains the safe
 * default for local development. Live-tested finding to respect when building the real
 * adapter: both tested models are reasoning models — parse
 * {@code message.content} only (not {@code .reasoning}), budget
 * {@code max_tokens >= 800}, and request
 * {@code response_format: {"type":"json_object"}} for reliable parsing.
 *
 * <p>To replace: implement {@link EssayScoringClient} with a real HTTP call,
 * remove {@code @Component} here (or gate both behind {@code scoring.ai.provider}).
 */
@Component
@ConditionalOnProperty(
        name = "scoring.ai.provider", havingValue = "stub", matchIfMissing = true)
public class StubEssayScoringClient implements EssayScoringClient {

    private static final int PLACEHOLDER_SCORE = 60;

    @Override
    public AiScoreResult score(String essayText, String promptText) {
        return new AiScoreResult(PLACEHOLDER_SCORE,
                Map.of("GRAMMAR", PLACEHOLDER_SCORE, "VOCABULARY", PLACEHOLDER_SCORE,
                        "SPELLING", PLACEHOLDER_SCORE, "WRITTEN_DISCOURSE", PLACEHOLDER_SCORE),
                "STUB: no real vendor configured yet (phase-09 documented gap).");
    }
}
