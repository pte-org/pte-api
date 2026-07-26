package com.pte.scoring.vendor.stub;

import com.pte.scoring.vendor.AiScoreResult;
import com.pte.scoring.vendor.SpeechScoringClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * PLACEHOLDER — makes NO network call, analyzes NO real audio. Returns a
 * deterministic mid-range score so the pipeline (queue → worker → status
 * transitions → events) is exercisable end-to-end before a real vendor is
 * wired in (phase-09: neither credential the user provided is audio-capable;
 * user's explicit direction was to build the framework now, plug in a real
 * adapter later — see phase-09 Design Constraints).
 *
 * <p>To replace: implement {@link SpeechScoringClient} against a real
 * audio-capable vendor (e.g. fetch bytes via media's presigned download —
 * not yet built, add when this class is replaced), remove
 * {@code @Component} here (or gate both behind {@code scoring.ai.provider}),
 * and the worker needs no changes — it only depends on the interface.
 */
@Component
public class StubSpeechScoringClient implements SpeechScoringClient {

    private static final int PLACEHOLDER_SCORE = 65;

    @Override
    public AiScoreResult score(String audioMediaPublicId, String referenceText) {
        return new AiScoreResult(PLACEHOLDER_SCORE,
                Map.of("ORAL_FLUENCY", PLACEHOLDER_SCORE, "PRONUNCIATION", PLACEHOLDER_SCORE),
                "STUB: no real vendor configured yet (phase-09 documented gap).");
    }
}
