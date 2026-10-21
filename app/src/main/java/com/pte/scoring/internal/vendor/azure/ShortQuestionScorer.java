package com.pte.scoring.internal.vendor.azure;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiAnswerScorer;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.audio.AudioChunk;
import com.pte.scoring.internal.vendor.speech.AnswerKey;
import com.pte.scoring.internal.vendor.speech.SpeechAudioLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Answer Short Question: recognition only, then 100 when the whole answer is
 * one of the accepted answers and 0 otherwise. No pronunciation pass, so no
 * cost beyond one recognition per chunk.
 */
@Component
@ConditionalOnProperty(name = "scoring.ai.provider", havingValue = "azure-gemini")
public class ShortQuestionScorer implements AiAnswerScorer {

    private final SpeechAudioLoader audio;
    private final AzureSpeechAssessor assessor;
    private final JsonMapper json;

    public ShortQuestionScorer(SpeechAudioLoader audio, AzureSpeechAssessor assessor, JsonMapper json) {
        this.audio = audio;
        this.assessor = assessor;
        this.json = json;
    }

    @Override
    public Set<String> taskTypes() {
        return Set.of(ScoringConstants.TASK_TYPE_ANSWER_SHORT_QUESTION);
    }

    @Override
    public AiScoreResult score(AiScoringRequest request) {
        AnswerKey key = AnswerKey.parse(request.correctAnswerText());
        if (key.acceptedAnswers().isEmpty()) {
            throw new AiProviderException(ScoringConstants.ANSWER_KEY_EMPTY);
        }
        List<AudioChunk> chunks = assessor.prepare(audio.load(request.payload(), request.tenantId()));
        SpeechAssessment heard = assessor.transcribe(chunks);
        String transcript = heard.displayText();
        // Display text may turn "seven" into "7"; the lexical form keeps the spoken words.
        Optional<String> matched = key.match(transcript).or(() -> key.match(heard.lexicalText()));

        int score = matched.isPresent() ? 100 : 0;
        Map<String, Object> detail = AzureScorers.newDetail();
        detail.put("matchedAnswer", matched.orElse(null));
        detail.put("acceptedAnswers", key.acceptedAnswers());
        return AzureScorers.result(json, score, Map.of("CONTENT", score), transcript, detail);
    }
}
