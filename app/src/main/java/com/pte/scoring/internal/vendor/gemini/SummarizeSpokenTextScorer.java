package com.pte.scoring.internal.vendor.gemini;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

/** Summarize Spoken Text: 50–70 words; Form comes from the word count alone. */
@Component
@ConditionalOnProperty(name = "scoring.ai.provider", havingValue = "azure-gemini")
public class SummarizeSpokenTextScorer extends GeminiWritingScorer {

    private static final String EXAMINER = "You are a PTE Academic examiner grading a Summarize Spoken Text "
            + "response. The candidate listened to a lecture and wrote a summary of it. Score each criterion "
            + "0-100: Content (coverage and accuracy of the source's key ideas), Grammar, Vocabulary and "
            + "Spelling. Form is calculated locally from the candidate word count and is not scored by you; do "
            + "not guess the word count. Penalize factual misrepresentation, irrelevant additions, and copying "
            + "long phrases from the source.";
    private static final String ASK = "Score each criterion 0-100, write brief feedback (2-3 sentences), list the "
            + "key points covered and missed, and list concrete language errors, each with the offending text and "
            + "a suggested correction.";

    public SummarizeSpokenTextScorer(GeminiRestClient gemini, JsonMapper json) {
        super(gemini, json, EXAMINER, "Source transcript", "Candidate's written summary", ASK,
                List.of("content", FORM, "grammar", "vocabulary", "spelling"), false,
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

    @Override
    FormCheck formCheck(String answer, AiScoringRequest request) {
        return FormCheck.summarizeSpokenText(answer, request.minWordCount(), request.maxWordCount());
    }
}
