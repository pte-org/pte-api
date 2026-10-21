package com.pte.scoring.internal.vendor.gemini;

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

/** Summarize Group Discussion: Gemini judges the topic, each speaker's contribution and how ideas relate. */
@Component
@ConditionalOnProperty(name = "scoring.ai.provider", havingValue = "azure-gemini")
public class SummarizeGroupDiscussionScorer extends GeminiSpeakingScorer {

    private static final String EXAMINER = "You are a PTE Academic examiner grading a Summarize Group Discussion "
            + "response. The candidate heard a discussion between several speakers and summarized it aloud. "
            + "Score Content 0-100: does the candidate accurately summarize the overall topic, each speaker's "
            + "important contribution, and the relationships (agreement, contrast, cause, consequence) between "
            + "ideas? Paraphrasing is expected. Do not score pronunciation or fluency. The [Speaker A], "
            + "[Speaker B], [Speaker C] labels in the source mark speaker boundaries: keep them in "
            + "speaker_contributions. List covered and missed points per speaker, repeat the most important "
            + "missed points in missed_points, and do not invent points the source does not support.";
    private static final String ASK = "Return the topic, the content score, each speaker's contribution, the "
            + "covered and missed points, the relationships and brief feedback (2-3 sentences).";
    private static final Map<String, Object> SCHEMA = GeminiScorers.object(
            "overall_topic", GeminiScorers.string(),
            "content", GeminiScorers.score(),
            "speaker_contributions", GeminiScorers.array(GeminiScorers.object(
                    "speaker", GeminiScorers.string(), "contribution_summary", GeminiScorers.string(),
                    "covered_points", GeminiScorers.strings(), "missed_points", GeminiScorers.strings(),
                    "relationships", GeminiScorers.strings())),
            "covered_points", GeminiScorers.strings(),
            "missed_points", GeminiScorers.strings(),
            "relationships", GeminiScorers.strings(),
            "feedback", GeminiScorers.string());

    public SummarizeGroupDiscussionScorer(SpeechAudioLoader audio, AzureSpeechAssessor assessor,
                                          GeminiRestClient gemini, JsonMapper json) {
        super(audio, assessor, gemini, json);
    }

    @Override
    public Set<String> taskTypes() {
        return Set.of(ScoringConstants.TASK_TYPE_SUMMARIZE_GROUP_DISCUSSION);
    }

    @Override
    Prepared prepare(AiScoringRequest request) {
        String source = requireSource(request.referenceAnswerText());
        return new Prepared(transcript -> new GeminiRequest(GeminiScorers.system(EXAMINER),
                GeminiScorers.quoted("Source discussion transcript", source)
                        + "\n" + GeminiScorers.quoted("Candidate's spoken summary", transcript) + "\n" + ASK,
                SCHEMA, List.of()), new LinkedHashMap<>(), false);
    }

    @Override
    String[] detailFields() {
        return new String[] {"overall_topic", "speaker_contributions", "covered_points", "missed_points",
                "relationships"};
    }

    @Override
    boolean missingProsodyScoresZero() {
        return false;
    }
}
