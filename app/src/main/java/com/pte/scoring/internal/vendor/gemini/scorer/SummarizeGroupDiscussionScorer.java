package com.pte.scoring.internal.vendor.gemini.scorer;

import com.pte.scoring.internal.vendor.gemini.StructuredModelClient;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.azure.AzureSpeechAssessor;
import com.pte.scoring.internal.vendor.speech.SpeechAudioLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Summarize Group Discussion (Score Guide pp.25-28): Gemini scores CONTENT 0-6, covering the topic, each speaker's
 * contribution and how ideas relate; the per-speaker breakdown is kept as detail only.
 */
@Component
@ConditionalOnProperty(name = "scoring.ai.provider", havingValue = "azure-gemini")
public class SummarizeGroupDiscussionScorer extends GeminiSpeakingScorer {

    static final String EXAMINER = "You are a PTE Academic examiner grading a Summarize Group Discussion "
            + "response. The candidate heard a discussion between several speakers and summarized it aloud; you "
            + "receive the transcript of what they said. Score CONTENT: does the candidate accurately summarize the "
            + "overall topic, each speaker's important contribution, and the relationships (agreement, contrast, "
            + "cause, consequence) between ideas? Paraphrasing is expected. The [Speaker A], [Speaker B], "
            + "[Speaker C] labels in the source mark speaker boundaries: keep them in speaker_contributions. List "
            + "covered and missed points per speaker, repeat the most important missed points in missed_points, "
            + "and do not invent points the source does not support.";
    private static final String ASK = "Score CONTENT on its scale as described above, then return brief feedback "
            + "(2-3 sentences), the topic, each speaker's contribution, the covered and missed points and the "
            + "relationships.";
    private static final Map<String, Object> DETAIL_SCHEMA = GeminiScorers.fields(
            "overall_topic", GeminiScorers.string(),
            "speaker_contributions", GeminiScorers.array(GeminiScorers.object(
                    "speaker", GeminiScorers.string(), "contribution_summary", GeminiScorers.string(),
                    "covered_points", GeminiScorers.strings(), "missed_points", GeminiScorers.strings(),
                    "relationships", GeminiScorers.strings())),
            "covered_points", GeminiScorers.strings(),
            "missed_points", GeminiScorers.strings(),
            "relationships", GeminiScorers.strings());

    public SummarizeGroupDiscussionScorer(SpeechAudioLoader audio, AzureSpeechAssessor assessor,
                                          StructuredModelClient gemini, JsonMapper json) {
        super(audio, assessor, gemini, json);
    }

    @Override
    public Set<String> taskTypes() {
        return Set.of(ScoringConstants.TASK_TYPE_SUMMARIZE_GROUP_DISCUSSION);
    }

    @Override
    Prepared prepare(AiScoringRequest request) {
        String source = requireSource(request.referenceAnswerText());
        return new Prepared(EXAMINER, transcript -> GeminiScorers.quoted("Source discussion transcript", source)
                + "\n" + GeminiScorers.quoted("Candidate's spoken summary", transcript) + "\n" + ASK, List.of(),
                new LinkedHashMap<>(), false);
    }

    @Override
    Map<String, Object> detailSchema() {
        return DETAIL_SCHEMA;
    }
}
