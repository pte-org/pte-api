package com.pte.scoring.internal.vendor.audioscoring;

import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.rubric.prompt.TraitScoreCollector;
import com.pte.scoring.internal.rubric.prompt.TraitScoreCollector.CollectedTraits;
import com.pte.scoring.internal.rubric.speech.SpeechBands;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.audio.AudioNormalizer;
import com.pte.scoring.internal.vendor.audio.PcmAudio;
import com.pte.scoring.internal.vendor.audioscoring.AudioTask.AudioSource;
import com.pte.scoring.internal.vendor.azure.AzureScorers;
import com.pte.scoring.internal.vendor.gemini.GeminiAudio;
import com.pte.scoring.internal.vendor.gemini.GeminiRequest;
import com.pte.scoring.internal.vendor.gemini.GeminiResponse;
import com.pte.scoring.internal.vendor.gemini.PromptImageLoader;
import com.pte.scoring.internal.vendor.gemini.PromptUsed;
import com.pte.scoring.internal.vendor.gemini.StructuredModelClient;
import com.pte.scoring.internal.vendor.speech.SpeechAudioLoader;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.RubricScorer;
import com.pte.scoring.rubric.TraitDefinition;
import com.pte.scoring.rubric.TraitScoreSet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Practice speaking scored by the scoring model hearing the recording itself (the Capstone gateway, or Gemini),
 * with the task's Pearson rubric in the prompt: it transcribes, judges every trait a listener can judge, and writes
 * the student's feedback in the same call. Traits counted by code come from its transcript; the item score is only
 * ever {@link RubricScorer}. A recording without speech scores 0 on every judged trait.
 */
@Component
@ConditionalOnProperty(name = "scoring.ai.provider", havingValue = "azure-gemini")
public class ModelAudioScorer implements PracticeAudioScorer {

    public static final String PROVIDER = "MODEL_AUDIO";
    private static final String MODEL_PREFIX = "audio+";
    private static final int MODEL_MAX_LENGTH = 160;
    private static final String ASK = "Score the attached recording against the rubric.";
    private static final String SCORED_BY = "scoredBy";
    private static final String AUDIO_DURATION = "audioDurationMs";
    private static final String SCHEMA_ATTEMPTS = "schemaAttempts";
    private static final String NO_SPEECH = "noSpeech";

    private final SpeechAudioLoader audio;
    private final StructuredModelClient model;
    private final JsonMapper json;
    private final Map<String, AudioTask> tasks;

    public ModelAudioScorer(SpeechAudioLoader audio, StructuredModelClient model, PromptImageLoader images,
                            JsonMapper json) {
        this.audio = audio;
        this.model = model;
        this.json = json;
        Map<String, AudioTask> byType = new HashMap<>();
        AudioTasks.all(images).forEach(task -> byType.put(task.taskType(), task));
        this.tasks = Map.copyOf(byType);
    }

    @Override
    public Set<String> taskTypes() {
        return tasks.keySet();
    }

    @Override
    public AiScoreResult score(AiScoringRequest request) {
        AudioTask task = tasks.get(request.taskType() == null ? "" : request.taskType().toUpperCase(Locale.ROOT));
        if (task == null) {
            throw new AiProviderException(ScoringConstants.UNSUPPORTED_AI_TASK_TYPE.formatted(request.taskType()));
        }
        EffectiveRubric rubric = SpeechBands.requireRubric(request);
        AudioSource source = task.source().apply(request);
        PcmAudio recording = AudioNormalizer.normalize(audio.load(request.payload(), request.tenantId()));

        Map<String, Object> detail = AzureScorers.newDetail();
        detail.put(SCORED_BY, PROVIDER);
        detail.putAll(source.detail());
        detail.put(AUDIO_DURATION, recording.durationMs());
        List<TraitDefinition> judged = AudioRubricPrompt.judgedTraits(rubric.task());
        Heard heard = recording.durationMs() == 0 ? Heard.silence() : listen(task, rubric, source, recording, judged);
        if (heard.attempts() != null) {
            detail.put(SCHEMA_ATTEMPTS, heard.attempts());
        }
        if (heard.prompt() != null) {
            detail.put(PromptUsed.DETAIL_KEY, heard.prompt());
        }
        boolean noSpeech = heard.transcript().isBlank();
        detail.put(NO_SPEECH, noSpeech);

        CountedTraits counted = CountedTraits.count(task.contentRule(), source.reference(), heard.transcript());
        detail.putAll(counted.detail());
        Map<String, Integer> traits = new LinkedHashMap<>();
        for (TraitDefinition trait : rubric.task().traits()) {
            Integer value = judged.contains(trait) ? heard.scores().get(trait.key()) : counted.scores().get(trait.key());
            traits.put(trait.key(), noSpeech || value == null ? 0 : value);
        }
        int itemScore = RubricScorer.score(rubric, new TraitScoreSet(traits, counted.wordCount())).itemScore();
        copyInto(detail, heard.content(), AudioRubricPrompt.TRAIT_RATIONALES, AudioRubricPrompt.TIPS);
        return new AiScoreResult(itemScore, traits, noSpeech ? "" : text(heard.content(), AudioRubricPrompt.FEEDBACK),
                AiProviderCategory.REAL, PROVIDER, heard.model(), null, heard.transcript(), serialize(detail),
                source.lowConfidence(), request.rubricVersionPublicId());
    }

    private Heard listen(AudioTask task, EffectiveRubric rubric, AudioSource source, PcmAudio recording,
                         List<TraitDefinition> judged) {
        GeminiRequest call = new GeminiRequest(AudioRubricPrompt.system(task.examiner(), rubric.task()),
                source.blocks() + "\n" + ASK, AudioRubricPrompt.schema(rubric.task()), source.images(),
                new GeminiAudio(GeminiAudio.WAV, recording.wav()), PracticeAudioPrompts.keyOf(task.taskType()));
        CollectedTraits<GeminiResponse> collected = TraitScoreCollector.collect(judged, () -> model.generate(call),
                GeminiResponse::content);
        JsonNode content = collected.response().content();
        String served = MODEL_PREFIX + collected.response().modelVersion();
        return new Heard(text(content, AudioRubricPrompt.TRANSCRIPT), collected.scores(), content,
                collected.attempts(), served.length() > MODEL_MAX_LENGTH ? served.substring(0, MODEL_MAX_LENGTH)
                : served, collected.response().prompt());
    }

    /** What the model heard: its transcript, the judged traits, its whole answer and how many calls it took. */
    private record Heard(String transcript, Map<String, Integer> scores, JsonNode content,
                         Map<String, Object> attempts, String model, PromptUsed prompt) {

        static Heard silence() {
            return new Heard("", Map.of(), null, null, PROVIDER, null);
        }
    }

    private static String text(JsonNode content, String field) {
        JsonNode value = content == null ? null : content.get(field);
        return value != null && value.isString() ? value.asText("").strip() : "";
    }

    private static void copyInto(Map<String, Object> detail, JsonNode content, String... fields) {
        if (content == null) {
            return;
        }
        for (String field : fields) {
            JsonNode value = content.get(field);
            if (value != null && !value.isNull()) {
                detail.put(field, value);
            }
        }
    }

    private String serialize(Map<String, Object> detail) {
        try {
            return json.writeValueAsString(detail);
        } catch (JacksonException ex) {
            throw new AiProviderException(ScoringConstants.AI_DETAIL_SERIALIZATION_FAILED, ex);
        }
    }
}
