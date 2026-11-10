package com.pte.scoring.internal.vendor.azure;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.rubric.speech.AzureBandMapping;
import com.pte.scoring.internal.rubric.speech.RepeatSentenceContent;
import com.pte.scoring.internal.rubric.speech.SpeechBands;
import com.pte.scoring.internal.vendor.AiAnswerScorer;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.azure.AzureScorers.ScriptedScore;
import com.pte.scoring.internal.vendor.speech.SpeechAudioLoader;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.RubricScorer;
import com.pte.scoring.rubric.TraitScoreSet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Set;

/**
 * Repeat Sentence through the rubric (Score Guide pp.16-17): Content 0-3 is counted in code from the words repeated
 * in sequence ({@link RepeatSentenceContent}); Pronunciation (Azure accuracy) and Oral Fluency (Azure fluency, with
 * prosody when present) are 0-5 bands from the rubric version's uncalibrated mapping, so fluency counts exactly
 * once; the item score is {@link RubricScorer} over the three traits.
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
        EffectiveRubric rubric = SpeechBands.requireRubric(request);
        AzureBandMapping mapping = SpeechBands.mappingOf(rubric);
        String sentence = AzureScorers.requireReference(request.referenceAnswerText());
        ScriptedScore scored = AzureScorers.scoreScripted(audio, assessor, request, sentence);
        SpeechAssessment assessed = scored.assessed();
        RepeatSentenceContent content = RepeatSentenceContent.measure(sentence, scored.transcript());
        SpeechBands bands = SpeechBands.of(mapping, assessed.accuracy(), assessed.fluency(), assessed.prosody());
        Map<String, Integer> traits = bands.traitsWithContent(content.band());
        int itemScore = RubricScorer.score(rubric, new TraitScoreSet(traits, null)).itemScore();

        Map<String, Object> detail = AzureScorers.newDetail();
        detail.put("repeatSentenceContent", content.detail());
        bands.writeInto(detail);
        detail.put("alignment", AzureScorers.alignmentDetail(scored.alignment()));
        detail.put("wordErrors", AzureScorers.wordErrors(assessed));
        detail.put("audioDurationMs", assessed.audioDurationMs());
        return AzureScorers.result(json, itemScore, traits, scored.transcript(), detail,
                request.rubricVersionPublicId());
    }
}
