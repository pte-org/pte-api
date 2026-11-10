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
import java.util.UUID;

/** What the Azure-backed speaking scorers share: provenance, the result envelope and the detail JSON. */
public final class AzureScorers {

    public static final String PROVIDER = "AZURE_SPEECH";
    public static final String MODEL = "short-audio-rest";
    /** Bands come from an uncalibrated Azure mapping, not a calibrated PTE model; the UI says so. */
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

    /** A read-out recording scored against its reference: what was heard, Azure's scores and the alignment. */
    record ScriptedScore(String transcript, SpeechAssessment assessed, TokenAlignment alignment) {
    }

    static ScriptedScore scoreScripted(SpeechAudioLoader audio, AzureSpeechAssessor assessor,
                                       AiScoringRequest request, String reference) {
        List<AudioChunk> chunks = assessor.prepare(audio.load(request.payload(), request.tenantId()));
        ScriptedAssessment scripted = assessor.assessScripted(chunks, reference);
        SpeechAssessment assessed = scripted.assessed();
        String transcript = scripted.heard().displayText();
        // The scored pass is forced onto the reference, so completeness is only visible in what was heard.
        TokenAlignment alignment = TokenAlignment.align(reference, transcript);
        return new ScriptedScore(transcript, assessed, alignment);
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

    /** A rubric result: {@code traits} are Pearson trait scores of the rubric version {@code rubricVersionPublicId}. */
    static AiScoreResult result(JsonMapper json, int rawScore, Map<String, Integer> traits, String transcript,
                                Map<String, Object> detail, UUID rubricVersionPublicId) {
        String detailJson;
        try {
            detailJson = json.writeValueAsString(detail);
        } catch (JacksonException ex) {
            throw new AiProviderException(ScoringConstants.AI_DETAIL_SERIALIZATION_FAILED, ex);
        }
        return new AiScoreResult(rawScore, traits, "", AiProviderCategory.REAL, PROVIDER, MODEL, null,
                transcript, detailJson, false, rubricVersionPublicId);
    }
}
