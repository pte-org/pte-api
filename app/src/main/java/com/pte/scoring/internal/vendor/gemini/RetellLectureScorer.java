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

/** Retell Lecture: Gemini judges how much of the lecture's content the retelling carries. */
@Component
@ConditionalOnProperty(name = "scoring.ai.provider", havingValue = "azure-gemini")
public class RetellLectureScorer extends GeminiSpeakingScorer {

    private static final String EXAMINER = "You are a PTE Academic examiner grading a Retell Lecture response. "
            + "The candidate listened to a lecture and must retell its content in their own words. "
            + "Score Content 0-100: does the retelling accurately cover the lecture's main points? Deduct for "
            + "missing key ideas, factual errors, or irrelevant additions. A good retelling does not need to be "
            + "word-for-word -- paraphrasing is expected and should not be penalized. Do not score pronunciation "
            + "or fluency; they are measured separately.";
    private static final String ASK = "Score Content 0-100, write brief feedback (2-3 sentences) noting which key "
            + "points were covered and which were missed, and list the main points from the lecture that were NOT "
            + "mentioned in the retelling (empty list if none missing).";
    private static final Map<String, Object> SCHEMA = GeminiScorers.object(
            "content", GeminiScorers.score(), "feedback", GeminiScorers.string(),
            "missed_points", GeminiScorers.strings());

    public RetellLectureScorer(SpeechAudioLoader audio, AzureSpeechAssessor assessor, GeminiRestClient gemini,
                               JsonMapper json) {
        super(audio, assessor, gemini, json);
    }

    @Override
    public Set<String> taskTypes() {
        return Set.of(ScoringConstants.TASK_TYPE_RE_TELL_LECTURE);
    }

    @Override
    Prepared prepare(AiScoringRequest request) {
        String lecture = requireSource(request.referenceAnswerText());
        return new Prepared(transcript -> new GeminiRequest(GeminiScorers.system(EXAMINER),
                GeminiScorers.quoted("Original lecture transcript", lecture)
                        + "\n" + GeminiScorers.quoted("Candidate's retelling", transcript) + "\n" + ASK,
                SCHEMA, List.of()), new LinkedHashMap<>(), false);
    }

    @Override
    String[] detailFields() {
        return new String[] {"missed_points"};
    }

    @Override
    boolean missingProsodyScoresZero() {
        return true;
    }
}
