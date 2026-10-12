package com.pte.scoring.internal.vendor.gemini;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiAnswerScorer;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.audio.AudioChunk;
import com.pte.scoring.internal.vendor.azure.AzureScorers;
import com.pte.scoring.internal.vendor.azure.AzureSpeechAssessor;
import com.pte.scoring.internal.vendor.azure.SpeechAssessment;
import com.pte.scoring.internal.vendor.speech.SpeechAudioLoader;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Unscripted speaking: Azure hears the recording (transcript, fluency, prosody)
 * and Gemini judges what was said against the task's source. The raw score is
 * the mean of Content, Fluency and Prosody; the subclass decides what a missing
 * Prosody means and what Gemini is asked.
 */
abstract class GeminiSpeakingScorer implements AiAnswerScorer {

    static final String CONTENT = "CONTENT";
    static final String FLUENCY = "FLUENCY";
    static final String PROSODY = "PROSODY";

    private static final String MODEL_PREFIX = AzureScorers.MODEL + "+";

    private final SpeechAudioLoader audio;
    private final AzureSpeechAssessor assessor;
    private final GeminiRestClient gemini;
    private final JsonMapper json;

    GeminiSpeakingScorer(SpeechAudioLoader audio, AzureSpeechAssessor assessor, GeminiRestClient gemini,
                         JsonMapper json) {
        this.audio = audio;
        this.assessor = assessor;
        this.gemini = gemini;
        this.json = json;
    }

    /**
     * What a task asks Gemini, built from the transcript once Azure has it.
     * {@code detail} is what the task knows before the audio is touched;
     * {@code lowConfidence} marks a score made without the task's full source.
     */
    record Prepared(Function<String, GeminiRequest> request, Map<String, Object> detail, boolean lowConfidence) {
    }

    /** Checks the item has what the task needs; runs before any audio is downloaded. */
    abstract Prepared prepare(AiScoringRequest request);

    /** The model fields kept in the stored detail, as the model wrote them. */
    abstract String[] detailFields();

    /** Gemini's content scores by sub-score name, {@value #CONTENT} first. */
    Map<String, Integer> contentScores(JsonNode content) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put(CONTENT, GeminiScorers.score(content, "content"));
        return scores;
    }

    /** Whether a recording with no Prosody still divides by three (a zero) or averages what exists. */
    abstract boolean missingProsodyScoresZero();

    @Override
    public AiScoreResult score(AiScoringRequest request) {
        Prepared prepared = prepare(request);
        List<AudioChunk> chunks = assessor.prepare(audio.load(request.payload(), request.tenantId()));
        SpeechAssessment assessed = assessor.assess(chunks, "");
        String transcript = assessed.displayText();

        Map<String, Object> detail = AzureScorers.newDetail();
        detail.putAll(prepared.detail());
        detail.put("speech", speechDetail(assessed));
        if (!assessed.speechDetected() || transcript.isBlank()) {
            return noSpeech(detail, prepared.lowConfidence());
        }

        GeminiResponse response = gemini.generate(prepared.request().apply(transcript));
        JsonNode content = response.content();
        Map<String, Integer> subScores = new LinkedHashMap<>(contentScores(content));
        List<Double> parts = new ArrayList<>();
        parts.add((double) subScores.get(CONTENT));
        double fluency = AzureScorers.orZero(assessed.fluency());
        subScores.put(FLUENCY, AzureScorers.points(fluency));
        parts.add(fluency);
        if (assessed.prosody() != null) {
            subScores.put(PROSODY, AzureScorers.points(assessed.prosody()));
            parts.add(assessed.prosody());
        } else if (missingProsodyScoresZero()) {
            subScores.put(PROSODY, 0);
            detail.put("prosodyUnavailable", true);
            parts.add(0.0);
        }
        int rawScore = AzureScorers.points(parts.stream().mapToDouble(Double::doubleValue).average().orElse(0));

        GeminiScorers.copyInto(detail, content, detailFields());
        return GeminiScorers.result(json, GeminiScorers.SPEAKING_PROVIDER,
                GeminiScorers.model(MODEL_PREFIX, response.modelVersion()), rawScore, subScores,
                GeminiScorers.text(content, "feedback"), transcript, detail, prepared.lowConfidence());
    }

    /** Nothing was said, so there is nothing for Gemini to judge: a valid low score, not a failure. */
    private AiScoreResult noSpeech(Map<String, Object> detail, boolean lowConfidence) {
        detail.put("noSpeech", true);
        Map<String, Integer> subScores = new LinkedHashMap<>();
        subScores.put(CONTENT, 0);
        subScores.put(FLUENCY, 0);
        subScores.put(PROSODY, 0);
        return GeminiScorers.result(json, AzureScorers.PROVIDER, AzureScorers.MODEL, 0, subScores, "", "", detail,
                lowConfidence);
    }

    private static Map<String, Object> speechDetail(SpeechAssessment assessed) {
        Map<String, Object> speech = new LinkedHashMap<>();
        speech.put("fluency", assessed.fluency());
        speech.put("prosody", assessed.prosody());
        speech.put("audioDurationMs", assessed.audioDurationMs());
        return speech;
    }

    /** The source text a task is judged against, or a permanent failure when the item has none. */
    static String requireSource(String text) {
        return GeminiScorers.requireText(text, ScoringConstants.AI_SOURCE_TEXT_EMPTY);
    }
}
