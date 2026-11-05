package com.pte.scoring.internal.rubric.speech;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.rubric.catalog.AzureBandMappingCatalog;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.RubricTraits;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Pronunciation and Oral Fluency bands of one recording, read from Azure's scores through the band-mapping table
 * the rubric version names. Shared by every speaking scorer so the machine-scored traits follow one rule everywhere.
 *
 * @param detail the raw Azure scores, the Oral Fluency input and the mapping used, for the stored detail JSON
 */
public record SpeechBands(int pronunciation, int oralFluency, boolean prosodyUnavailable,
                          Map<String, Object> detail) {

    public static final String SPEECH_DETAIL = "speech";
    public static final String PROSODY_UNAVAILABLE = "prosodyUnavailable";

    /** A speaking scorer needs the rubric the job resolved; without it the answer fails closed, before any audio. */
    public static EffectiveRubric requireRubric(AiScoringRequest request) {
        if (request.rubric() == null) {
            throw new AiProviderException(ScoringConstants.AI_RUBRIC_MISSING);
        }
        return request.rubric();
    }

    /** The mapping table of the rubric version; an unknown table fails closed instead of guessing bands. */
    public static AzureBandMapping mappingOf(EffectiveRubric rubric) {
        return AzureBandMappingCatalog.find(rubric.parameters().azureMappingVersion())
                .orElseThrow(() -> new AiProviderException(ScoringConstants.AI_AZURE_BAND_MAPPING_UNKNOWN));
    }

    public static SpeechBands of(AzureBandMapping mapping, Double accuracy, Double fluency, Double prosody) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("accuracy", accuracy);
        detail.put("fluency", fluency);
        detail.put("prosody", prosody);
        detail.put("oralFluencyInput", AzureBandMapping.oralFluencyInput(fluency, prosody));
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("version", mapping.version());
        table.put("calibrated", mapping.calibrated());
        detail.put("azureBandMapping", table);
        return new SpeechBands(mapping.pronunciationBand(accuracy), mapping.oralFluencyBand(fluency, prosody),
                prosody == null, detail);
    }

    /** The three speaking traits in the Score Guide's order: Content, then the two machine-scored bands. */
    public Map<String, Integer> traitsWithContent(int content) {
        Map<String, Integer> traits = new LinkedHashMap<>();
        traits.put(RubricTraits.CONTENT, content);
        traits.put(RubricTraits.PRONUNCIATION, pronunciation);
        traits.put(RubricTraits.ORAL_FLUENCY, oralFluency);
        return traits;
    }

    /** Adds the Azure detail and, when Azure returned no prosody, the {@value #PROSODY_UNAVAILABLE} flag. */
    public void writeInto(Map<String, Object> target) {
        target.put(SPEECH_DETAIL, new LinkedHashMap<>(detail));
        if (prosodyUnavailable) {
            target.put(PROSODY_UNAVAILABLE, true);
        }
    }
}
