package com.pte.scoring.internal.vendor.gemini.scorer;

import com.pte.scoring.internal.vendor.gemini.GeminiRequest;
import com.pte.scoring.internal.vendor.gemini.GeminiRestClient;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.azure.AzureSpeechAssessor;
import com.pte.scoring.internal.vendor.speech.SpeechAudioLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Respond to a Situation: Gemini judges content, task fulfilment, relevance and register. */
@Component
@ConditionalOnProperty(name = "scoring.ai.provider", havingValue = "azure-gemini")
public class RespondToSituationScorer extends GeminiSpeakingScorer {

    private static final String EXAMINER = "You are evaluating a PTE-style Respond to a Situation response. "
            + "Read the situation and the candidate's spoken response. Infer the practical requirements and the "
            + "expected formal or informal register of the situation. Score each field 0-100: content (overall "
            + "quality and appropriateness), task_fulfillment (does what the situation asks), relevance (stays "
            + "focused), register_score (tone and politeness fit). List every important requirement as "
            + "addressed, missed or misinterpreted with short quoted evidence, and keep missed_requirements and "
            + "misinterpreted_requirements explicit. Do not score pronunciation or fluency (they are measured "
            + "separately) and do not reward unsupported details or verbosity.";
    private static final String ASK = "Return the scores, the requirement check and brief feedback (2-3 sentences).";
    private static final Map<String, Object> SCHEMA = GeminiScorers.object(
            "content", GeminiScorers.score(),
            "task_fulfillment", GeminiScorers.score(),
            "relevance", GeminiScorers.score(),
            "register_score", GeminiScorers.score(),
            "expected_register", GeminiScorers.string(),
            "required_facts", GeminiScorers.array(GeminiScorers.object(
                    "requirement", GeminiScorers.string(), "status", GeminiScorers.string(),
                    "evidence", GeminiScorers.string())),
            "missed_requirements", GeminiScorers.strings(),
            "misinterpreted_requirements", GeminiScorers.strings(),
            "feedback", GeminiScorers.string());

    public RespondToSituationScorer(SpeechAudioLoader audio, AzureSpeechAssessor assessor, GeminiRestClient gemini,
                                    JsonMapper json) {
        super(audio, assessor, gemini, json);
    }

    @Override
    public Set<String> taskTypes() {
        return Set.of(ScoringConstants.TASK_TYPE_RESPOND_TO_A_SITUATION);
    }

    @Override
    Prepared prepare(AiScoringRequest request) {
        String situation = requireSource(request.promptText());
        return new Prepared(transcript -> new GeminiRequest(GeminiScorers.system(EXAMINER),
                GeminiScorers.quoted("Situation prompt", situation)
                        + "\n" + GeminiScorers.quoted("Candidate's spoken response", transcript) + "\n" + ASK,
                SCHEMA, List.of()), new LinkedHashMap<>(), false);
    }

    @Override
    Map<String, Integer> contentScores(JsonNode content) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put(CONTENT, GeminiScorers.score(content, "content"));
        scores.put("TASK_FULFILMENT", GeminiScorers.score(content, "task_fulfillment"));
        scores.put("RELEVANCE", GeminiScorers.score(content, "relevance"));
        scores.put("REGISTER", GeminiScorers.score(content, "register_score"));
        return scores;
    }

    @Override
    String[] detailFields() {
        return new String[] {"expected_register", "required_facts", "missed_requirements",
                "misinterpreted_requirements"};
    }

    @Override
    boolean missingProsodyScoresZero() {
        return false;
    }
}
