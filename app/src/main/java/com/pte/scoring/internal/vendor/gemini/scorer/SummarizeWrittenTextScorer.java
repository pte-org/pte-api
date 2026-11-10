package com.pte.scoring.internal.vendor.gemini.scorer;

import com.pte.scoring.internal.vendor.gemini.StructuredModelClient;
import com.pte.scoring.internal.constant.ScoringConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

/** Summarize Written Text (Score Guide pp.32-33): Gemini scores Content, Grammar and Vocabulary; code counts Form. */
@Component
@ConditionalOnProperty(name = "scoring.ai.provider", havingValue = "azure-gemini")
public class SummarizeWrittenTextScorer extends GeminiWritingScorer {

    static final String EXAMINER = "You are a PTE Academic examiner grading a Summarize Written Text "
            + "response. The candidate read a passage and wrote a one-sentence summary of it.";
    private static final String ASK = "Score each trait on its own scale as described above, write brief feedback "
            + "(2-3 sentences), and list specific grammar/spelling/vocabulary errors found, each with the offending "
            + "text and a suggested correction.";

    public SummarizeWrittenTextScorer(StructuredModelClient gemini, JsonMapper json) {
        super(gemini, json, EXAMINER, "Passage", "Candidate's summary", ASK, List.of());
    }

    @Override
    public Set<String> taskTypes() {
        return Set.of(ScoringConstants.TASK_TYPE_SUMMARIZE_WRITTEN_TEXT);
    }
}
