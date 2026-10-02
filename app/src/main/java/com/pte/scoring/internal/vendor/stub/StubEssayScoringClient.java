package com.pte.scoring.internal.vendor.stub;

import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.EssayScoringClient;
import com.pte.scoring.domain.enums.AiProviderCategory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * PLACEHOLDER — makes NO network call. The OpenAI-compatible adapter is
 * opt-in through {@code scoring.ai.provider=openai-compatible}; the stub
 * remains the safe default for local development.
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
                "STUB: no real vendor configured yet.", AiProviderCategory.STUB, "STUB", null, null);
    }
}
