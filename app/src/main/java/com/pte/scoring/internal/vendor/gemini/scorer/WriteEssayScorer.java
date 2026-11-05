package com.pte.scoring.internal.vendor.gemini.scorer;

import com.pte.scoring.internal.vendor.gemini.GeminiRestClient;
import com.pte.scoring.internal.constant.ScoringConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

/** Write Essay (Score Guide pp.34-39): Gemini scores six of the seven traits; code counts Form. */
@Component
@ConditionalOnProperty(name = "scoring.ai.provider", havingValue = "azure-gemini")
public class WriteEssayScorer extends GeminiWritingScorer {

    private static final String EXAMINER = "You are a PTE Academic examiner grading a Write Essay response. The "
            + "candidate wrote an essay on the given prompt.";
    private static final String ASK = "Score each trait on its own scale as described above, write brief feedback "
            + "(2-3 sentences), and list specific grammar/spelling/vocabulary errors found, each with the offending "
            + "text and a suggested correction.";

    public WriteEssayScorer(GeminiRestClient gemini, JsonMapper json) {
        super(gemini, json, EXAMINER, "Essay prompt", "Candidate's essay", ASK, List.of());
    }

    @Override
    public Set<String> taskTypes() {
        return Set.of(ScoringConstants.TASK_TYPE_WRITE_ESSAY);
    }
}
