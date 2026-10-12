package com.pte.scoring.internal.vendor.azure;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiAnswerScorer;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.azure.AzureScorers.ScriptedScore;
import com.pte.scoring.internal.vendor.speech.SpeechAudioLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Read Aloud: the score is the pronunciation score of the whole recording,
 * from Azure's merged accuracy, fluency and prosody and a completeness the
 * backend measures by aligning what was heard against the passage.
 */
@Component
@ConditionalOnProperty(name = "scoring.ai.provider", havingValue = "azure-gemini")
public class ReadAloudScorer implements AiAnswerScorer {

    private final SpeechAudioLoader audio;
    private final AzureSpeechAssessor assessor;
    private final JsonMapper json;

    public ReadAloudScorer(SpeechAudioLoader audio, AzureSpeechAssessor assessor, JsonMapper json) {
        this.audio = audio;
        this.assessor = assessor;
        this.json = json;
    }

    @Override
    public Set<String> taskTypes() {
        return Set.of(ScoringConstants.TASK_TYPE_READ_ALOUD);
    }

    @Override
    public AiScoreResult score(AiScoringRequest request) {
        String passage = AzureScorers.requireReference(request.promptText());
        ScriptedScore scored = AzureScorers.scoreScripted(audio, assessor, request, passage);
        SpeechAssessment assessed = scored.assessed();

        Map<String, Integer> subScores = new LinkedHashMap<>();
        subScores.put("PRONUNCIATION", AzureScorers.points(scored.pronunciation()));
        subScores.put("ACCURACY", AzureScorers.points(AzureScorers.orZero(assessed.accuracy())));
        subScores.put("FLUENCY", AzureScorers.points(AzureScorers.orZero(assessed.fluency())));
        if (assessed.prosody() != null) {
            subScores.put("PROSODY", AzureScorers.points(assessed.prosody()));
        }
        subScores.put("COMPLETENESS", AzureScorers.points(scored.alignment().recall()));

        Map<String, Object> detail = AzureScorers.newDetail();
        detail.put("pronunciation", scored.pronunciation());
        detail.put("alignment", AzureScorers.alignmentDetail(scored.alignment()));
        detail.put("wordErrors", AzureScorers.wordErrors(assessed));
        detail.put("omittedWords", scored.alignment().missingTokens());
        detail.put("audioDurationMs", assessed.audioDurationMs());
        return AzureScorers.result(json, AzureScorers.points(scored.pronunciation()), subScores,
                scored.transcript(), detail);
    }
}
