package com.pte.scoring.internal.vendor.azure;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.rubric.speech.AzureBandMapping;
import com.pte.scoring.internal.rubric.speech.ReadAloudContent;
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
 * Read Aloud through the rubric (Score Guide pp.15-16): Content is counted in code as N passage words minus every
 * replaced, omitted or inserted word ({@link ReadAloudContent}); Pronunciation and Oral Fluency are Azure's scores
 * through the rubric version's uncalibrated band mapping; the item score is {@link RubricScorer} over the three traits
 * with N as the per-word maximum. N is stored in the detail ({@code wordCount}) so the item can be rescored later.
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
        EffectiveRubric rubric = SpeechBands.requireRubric(request);
        AzureBandMapping mapping = SpeechBands.mappingOf(rubric);
        String passage = AzureScorers.requireReference(request.promptText());
        ScriptedScore scored = AzureScorers.scoreScripted(audio, assessor, request, passage);
        SpeechAssessment assessed = scored.assessed();
        ReadAloudContent content = ReadAloudContent.measure(passage, scored.transcript());
        SpeechBands bands = SpeechBands.of(mapping, assessed.accuracy(), assessed.fluency(), assessed.prosody());
        Map<String, Integer> traits = bands.traitsWithContent(content.achieved());
        int itemScore = RubricScorer.score(rubric, new TraitScoreSet(traits, content.wordCount())).itemScore();

        Map<String, Object> detail = AzureScorers.newDetail();
        detail.put(ScoringConstants.DETAIL_WORD_COUNT, content.wordCount());
        detail.put("readAloudContent", content.detail());
        bands.writeInto(detail);
        detail.put("alignment", AzureScorers.alignmentDetail(scored.alignment()));
        detail.put("wordErrors", AzureScorers.wordErrors(assessed));
        detail.put("omittedWords", scored.alignment().missingTokens());
        detail.put("audioDurationMs", assessed.audioDurationMs());
        return AzureScorers.result(json, itemScore, traits, scored.transcript(), detail,
                request.rubricVersionPublicId());
    }
}
