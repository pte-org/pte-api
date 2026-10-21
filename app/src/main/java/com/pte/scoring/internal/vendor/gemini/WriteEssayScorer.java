package com.pte.scoring.internal.vendor.gemini;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

/** Write Essay: 200–300 words, scored on seven criteria. */
@Component
@ConditionalOnProperty(name = "scoring.ai.provider", havingValue = "azure-gemini")
public class WriteEssayScorer extends GeminiWritingScorer {

    private static final String EXAMINER = "You are a PTE Academic examiner grading a Write Essay response. The "
            + "candidate wrote an essay on the given prompt. Score each criterion 0-100: Content (relevance to "
            + "the prompt and quality of the argument), Form (essay structure and paragraphing; length is "
            + "checked separately), Development, structure and coherence, Grammar, General linguistic range, "
            + "Vocabulary range and accuracy, and Spelling.";
    private static final String ASK = "Score each criterion 0-100, write brief feedback (2-3 sentences), and list "
            + "specific grammar/spelling/vocabulary errors found, each with the offending text and a suggested "
            + "correction.";

    public WriteEssayScorer(GeminiRestClient gemini, JsonMapper json) {
        super(gemini, json, EXAMINER, "Essay prompt", "Candidate's essay", ASK,
                List.of("content", FORM, "development", "grammar", "general_linguistic_range", "vocabulary",
                        "spelling"), true, List.of());
    }

    @Override
    public Set<String> taskTypes() {
        return Set.of(ScoringConstants.TASK_TYPE_WRITE_ESSAY);
    }

    @Override
    FormCheck formCheck(String answer, AiScoringRequest request) {
        return FormCheck.essay(answer, request.minWordCount(), request.maxWordCount());
    }
}
