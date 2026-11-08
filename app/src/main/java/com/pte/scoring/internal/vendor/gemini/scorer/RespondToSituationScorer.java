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
 * Respond to a Situation (Score Guide pp.29-31): Gemini scores CONTENT 0-6 (task fulfilment, relevance, register) and
 * lists every requirement of the situation as addressed, missed or misinterpreted; the list is kept as detail only.
 */
@Component
@ConditionalOnProperty(name = "scoring.ai.provider", havingValue = "azure-gemini")
public class RespondToSituationScorer extends GeminiSpeakingScorer {

    static final String EXAMINER = "You are a PTE Academic examiner grading a Respond to a Situation "
            + "response. Read the situation and the transcript of the candidate's spoken response. Infer the "
            + "practical requirements and the expected formal or informal register of the situation. Score CONTENT: "
            + "does the response achieve the communication goal of the situation, stay relevant, and use a tone "
            + "and politeness that fit it? List every important requirement as addressed, missed or misinterpreted "
            + "with short quoted evidence, and keep missed_requirements and misinterpreted_requirements explicit. "
            + "Do not reward unsupported details or verbosity.";
    private static final String ASK = "Score CONTENT on its scale as described above, then return brief feedback "
            + "(2-3 sentences), the expected register and the requirement check.";
    private static final Map<String, Object> DETAIL_SCHEMA = GeminiScorers.fields(
            "expected_register", GeminiScorers.string(),
            "required_facts", GeminiScorers.array(GeminiScorers.object(
                    "requirement", GeminiScorers.string(), "status", GeminiScorers.string(),
                    "evidence", GeminiScorers.string())),
            "missed_requirements", GeminiScorers.strings(),
            "misinterpreted_requirements", GeminiScorers.strings());

    public RespondToSituationScorer(SpeechAudioLoader audio, AzureSpeechAssessor assessor, StructuredModelClient gemini,
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
        return new Prepared(EXAMINER, transcript -> GeminiScorers.quoted("Situation prompt", situation)
                + "\n" + GeminiScorers.quoted("Candidate's spoken response", transcript) + "\n" + ASK, List.of(),
                new LinkedHashMap<>(), false);
    }

    @Override
    Map<String, Object> detailSchema() {
        return DETAIL_SCHEMA;
    }
}
