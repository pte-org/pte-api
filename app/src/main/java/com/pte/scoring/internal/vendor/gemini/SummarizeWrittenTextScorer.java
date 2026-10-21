package com.pte.scoring.internal.vendor.gemini;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

/** Summarize Written Text: one sentence of 5–75 words, scored on content, form, grammar and vocabulary. */
@Component
@ConditionalOnProperty(name = "scoring.ai.provider", havingValue = "azure-gemini")
public class SummarizeWrittenTextScorer extends GeminiWritingScorer {

    private static final String EXAMINER = "You are a PTE Academic examiner grading a Summarize Written Text "
            + "response. The candidate read a passage and wrote a one-sentence summary of it. Score each "
            + "criterion 0-100: Content (the summary captures the main idea(s) accurately and completely), "
            + "Form (a well-formed single sentence with correct structure; word and sentence-count limits are "
            + "checked separately -- judge sentence quality, not length), Grammar (grammatical correctness and "
            + "punctuation) and Vocabulary (range and appropriateness of word choice).";
    private static final String ASK = "Score each criterion 0-100, write brief feedback (2-3 sentences), and list "
            + "specific grammar/spelling/vocabulary errors found, each with the offending text and a suggested "
            + "correction.";

    public SummarizeWrittenTextScorer(GeminiRestClient gemini, JsonMapper json) {
        super(gemini, json, EXAMINER, "Passage", "Candidate's summary", ASK,
                List.of("content", FORM, "grammar", "vocabulary"), true, List.of());
    }

    @Override
    public Set<String> taskTypes() {
        return Set.of(ScoringConstants.TASK_TYPE_SUMMARIZE_WRITTEN_TEXT);
    }

    @Override
    FormCheck formCheck(String answer, AiScoringRequest request) {
        return FormCheck.summarizeWrittenText(answer, request.minWordCount(), request.maxWordCount());
    }
}
