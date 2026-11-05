package com.pte.scoring.internal.vendor.gemini.scorer;

import com.pte.scoring.internal.vendor.gemini.GeminiRestClient;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

/** Summarize Spoken Text (Score Guide pp.41-43): Gemini scores four traits; code counts Form. */
@Component
@ConditionalOnProperty(name = "scoring.ai.provider", havingValue = "azure-gemini")
public class SummarizeSpokenTextScorer extends GeminiWritingScorer {

    private static final String EXAMINER = "You are a PTE Academic examiner grading a Summarize Spoken Text "
            + "response. The candidate listened to a lecture and wrote a summary of it. Penalize factual "
            + "misrepresentation, irrelevant additions, and copying long phrases from the source.";
    private static final String ASK = "Score each trait on its own scale as described above, write brief feedback "
            + "(2-3 sentences), list the key points covered and missed, and list concrete language errors, each with "
            + "the offending text and a suggested correction.";

    public SummarizeSpokenTextScorer(GeminiRestClient gemini, JsonMapper json) {
        super(gemini, json, EXAMINER, "Source transcript", "Candidate's written summary", ASK,
                List.of("covered_points", "missed_points"));
    }

    @Override
    public Set<String> taskTypes() {
        return Set.of(ScoringConstants.TASK_TYPE_SUMMARIZE_SPOKEN_TEXT);
    }

    /** The lecture's transcript is authored as the item's reference answer; the item has no prompt text. */
    @Override
    String sourceText(AiScoringRequest request) {
        return request.referenceAnswerText();
    }
}
