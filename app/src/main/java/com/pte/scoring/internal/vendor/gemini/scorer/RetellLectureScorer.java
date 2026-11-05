package com.pte.scoring.internal.vendor.gemini.scorer;

import com.pte.scoring.internal.vendor.gemini.GeminiRestClient;
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

/** Re-tell Lecture (Score Guide pp.21-24): Gemini scores CONTENT 0-6, how much of the lecture the retelling carries. */
@Component
@ConditionalOnProperty(name = "scoring.ai.provider", havingValue = "azure-gemini")
public class RetellLectureScorer extends GeminiSpeakingScorer {

    static final String MISSED_POINTS = "missed_points";

    private static final String EXAMINER = "You are a PTE Academic examiner grading a Re-tell Lecture response. "
            + "The candidate listened to a lecture and retold its content in their own words; you receive the "
            + "transcript of what they said. Score CONTENT: does the retelling accurately cover the lecture's main "
            + "points and how they relate? Deduct for missing key ideas, factual errors, or irrelevant additions. "
            + "A good retelling does not need to be word-for-word: paraphrasing is expected and is not penalized.";
    private static final String ASK = "Score CONTENT on its scale as described above, write brief feedback (2-3 "
            + "sentences) noting which key points were covered and which were missed, and list in missed_points the "
            + "main points from the lecture that were NOT mentioned in the retelling (empty list if none missing).";

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
        return new Prepared(EXAMINER, transcript -> GeminiScorers.quoted("Original lecture transcript", lecture)
                + "\n" + GeminiScorers.quoted("Candidate's retelling", transcript) + "\n" + ASK, List.of(),
                new LinkedHashMap<>(), false);
    }

    @Override
    Map<String, Object> detailSchema() {
        return Map.of(MISSED_POINTS, GeminiScorers.strings());
    }
}
