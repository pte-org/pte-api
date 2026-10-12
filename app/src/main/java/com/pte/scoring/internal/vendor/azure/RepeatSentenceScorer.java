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
 * Repeat Sentence: the mean of how closely the words repeated match the
 * sentence (alignment F1), the pronunciation score and fluency.
 */
@Component
@ConditionalOnProperty(name = "scoring.ai.provider", havingValue = "azure-gemini")
public class RepeatSentenceScorer implements AiAnswerScorer {

    private final SpeechAudioLoader audio;
    private final AzureSpeechAssessor assessor;
    private final JsonMapper json;

    public RepeatSentenceScorer(SpeechAudioLoader audio, AzureSpeechAssessor assessor, JsonMapper json) {
        this.audio = audio;
        this.assessor = assessor;
        this.json = json;
    }

    @Override
    public Set<String> taskTypes() {
        return Set.of(ScoringConstants.TASK_TYPE_REPEAT_SENTENCE);
    }

    @Override
    public AiScoreResult score(AiScoringRequest request) {
        String sentence = AzureScorers.requireReference(request.referenceAnswerText());
        ScriptedScore scored = AzureScorers.scoreScripted(audio, assessor, request, sentence);
        SpeechAssessment assessed = scored.assessed();
        double content = scored.alignment().score();
        double fluency = AzureScorers.orZero(assessed.fluency());
        double overall = (content + scored.pronunciation() + fluency) / 3;

        Map<String, Integer> subScores = new LinkedHashMap<>();
        subScores.put("CONTENT", AzureScorers.points(content));
        subScores.put("PRONUNCIATION", AzureScorers.points(scored.pronunciation()));
        subScores.put("FLUENCY", AzureScorers.points(fluency));

        Map<String, Object> detail = AzureScorers.newDetail();
        detail.put("pronunciation", scored.pronunciation());
        detail.put("alignment", AzureScorers.alignmentDetail(scored.alignment()));
        detail.put("wordErrors", AzureScorers.wordErrors(assessed));
        detail.put("audioDurationMs", assessed.audioDurationMs());
        return AzureScorers.result(json, AzureScorers.points(overall), subScores, scored.transcript(), detail);
    }
}
