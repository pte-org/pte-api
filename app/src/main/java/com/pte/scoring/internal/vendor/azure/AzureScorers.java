package com.pte.scoring.internal.vendor.azure;

import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.audio.AudioChunk;
import com.pte.scoring.internal.vendor.speech.SpeechAudioLoader;
import com.pte.scoring.internal.vendor.speech.TokenAlignment;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** What the Azure-backed speaking scorers share: provenance, the result envelope and the detail JSON. */
public final class AzureScorers {

    public static final String PROVIDER = "AZURE_SPEECH";
    public static final String MODEL = "short-audio-rest";
    /** Scores come from an unvalidated formula, not a calibrated PTE model; the UI says so. */
    public static final String SCORE_LABEL = "UNVALIDATED";

    private static final Set<String> CORRECT_WORD = Set.of("None", "Omission", "Insertion");

    private AzureScorers() {
    }

    /** The reference a scripted task is read against; a text with no words cannot be scored. */
    static String requireReference(String reference) {
        if (reference == null || TokenAlignment.normalize(reference).isEmpty()) {
            throw new AiProviderException(ScoringConstants.SPEECH_REFERENCE_TEXT_EMPTY);
        }
        return reference;
    }

    public static int points(double score) {
        return (int) Math.round(Math.max(0, Math.min(100, score)));
    }

    public static double orZero(Double score) {
        return score == null ? 0 : score;
    }

    /** A read-out recording scored against its reference: what was heard, Azure's scores and the alignment. */
    record ScriptedScore(String transcript, SpeechAssessment assessed, TokenAlignment alignment,
                         double pronunciation) {
    }

    static ScriptedScore scoreScripted(SpeechAudioLoader audio, AzureSpeechAssessor assessor,
                                       AiScoringRequest request, String reference) {
        List<AudioChunk> chunks = assessor.prepare(audio.load(request.payload(), request.tenantId()));
        ScriptedAssessment scripted = assessor.assessScripted(chunks, reference);
        SpeechAssessment assessed = scripted.assessed();
        String transcript = scripted.heard().displayText();
        // The scored pass is forced onto the reference, so completeness is only visible in what was heard.
        TokenAlignment alignment = TokenAlignment.align(reference, transcript);
        double pronunciation = PronunciationFormula.score(assessed.accuracy(), assessed.fluency(),
                assessed.prosody(), alignment.recall());
        return new ScriptedScore(transcript, assessed, alignment, pronunciation);
    }

    public static Map<String, Object> newDetail() {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("scoreLabel", SCORE_LABEL);
        return detail;
    }

    static Map<String, Object> alignmentDetail(TokenAlignment alignment) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("score", alignment.score());
        detail.put("precision", alignment.precision());
        detail.put("recall", alignment.recall());
        detail.put("missingTokens", alignment.missingTokens());
        detail.put("extraTokens", alignment.extraTokens());
        return detail;
    }

    /** Words Azure judged wrongly pronounced; omissions are the alignment's business, not Azure's. */
    static List<Map<String, Object>> wordErrors(SpeechAssessment assessed) {
        List<Map<String, Object>> errors = new ArrayList<>();
        for (SpeechWord word : assessed.words()) {
            if (word.errorType() != null && !word.errorType().isBlank() && !CORRECT_WORD.contains(word.errorType())) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("word", word.word());
                entry.put("errorType", word.errorType());
                entry.put("accuracy", word.accuracy());
                errors.add(entry);
            }
        }
        return errors;
    }

    static AiScoreResult result(JsonMapper json, int rawScore, Map<String, Integer> subScores, String transcript,
                                Map<String, Object> detail) {
        String detailJson;
        try {
            detailJson = json.writeValueAsString(detail);
        } catch (JacksonException ex) {
            throw new AiProviderException(ScoringConstants.AI_DETAIL_SERIALIZATION_FAILED, ex);
        }
        return new AiScoreResult(rawScore, subScores, "", AiProviderCategory.REAL, PROVIDER, MODEL, null,
                transcript, detailJson, false);
    }
}
