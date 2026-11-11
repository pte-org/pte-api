package com.pte.scoring.internal.vendor.gemini.scorer;

import com.pte.scoring.internal.rubric.prompt.RubricPromptBuilder;
import com.pte.scoring.internal.rubric.prompt.RubricSchemaBuilder;
import com.pte.scoring.internal.rubric.prompt.TraitScoreCollector;
import com.pte.scoring.internal.rubric.prompt.TraitScoreCollector.CollectedTraits;
import com.pte.scoring.internal.rubric.speech.AzureBandMapping;
import com.pte.scoring.internal.rubric.speech.SpeechBands;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiAnswerScorer;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.audio.AudioChunk;
import com.pte.scoring.internal.vendor.azure.AzureScorers;
import com.pte.scoring.internal.vendor.azure.AzureSpeechAssessor;
import com.pte.scoring.internal.vendor.azure.SpeechAssessment;
import com.pte.scoring.internal.vendor.gemini.GeminiImage;
import com.pte.scoring.internal.vendor.gemini.GeminiRequest;
import com.pte.scoring.internal.vendor.gemini.GeminiResponse;
import com.pte.scoring.internal.vendor.gemini.StructuredModelClient;
import com.pte.scoring.internal.vendor.speech.SpeechAudioLoader;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.RubricScorer;
import com.pte.scoring.rubric.RubricTraits;
import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TraitScoreSet;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Unscripted speaking (Describe Image, Re-tell Lecture, Respond to a Situation, Summarize Group Discussion) through
 * the rubric of the request: Azure hears the recording; Gemini scores only CONTENT, one whole number on its Pearson
 * scale (prompt and schema from the catalog, bounded retry, never rounded); Pronunciation (Azure accuracy) and Oral
 * Fluency (Azure fluency, with prosody when present) are 0-5 bands from the version's uncalibrated mapping; the item
 * score is {@link RubricScorer}, so Content 0 gates the item to 0. A recording without speech is a valid 0 on every
 * trait without a model call.
 */
abstract class GeminiSpeakingScorer implements AiAnswerScorer {

    private static final String MODEL_PREFIX = AzureScorers.MODEL + "+";
    private static final String SCHEMA_ATTEMPTS = "schemaAttempts";
    private static final String NO_SPEECH = "noSpeech";
    private static final String AUDIO_DURATION = "audioDurationMs";

    private final SpeechAudioLoader audio;
    private final AzureSpeechAssessor assessor;
    private final StructuredModelClient gemini;
    private final JsonMapper json;

    GeminiSpeakingScorer(SpeechAudioLoader audio, AzureSpeechAssessor assessor, StructuredModelClient gemini,
                         JsonMapper json) {
        this.audio = audio;
        this.assessor = assessor;
        this.gemini = gemini;
        this.json = json;
    }

    /**
     * What a task asks Gemini once Azure has the transcript. {@code examiner} is the role (the catalog's rubric
     * section and the injection guard follow it); {@code userText} builds the quoted data blocks and the request;
     * {@code detail} is what the task knows before the audio is touched; {@code lowConfidence} marks a score made
     * without the task's full source.
     */
    record Prepared(String examiner, Function<String, String> userText, List<GeminiImage> images,
                    Map<String, Object> detail, boolean lowConfidence) {
    }

    /** Checks the item has what the task needs; runs before any audio is downloaded. */
    abstract Prepared prepare(AiScoringRequest request);

    /** The task's descriptive fields Gemini returns after its feedback, in order; kept in the stored detail. */
    abstract Map<String, Object> detailSchema();

    @Override
    public AiScoreResult score(AiScoringRequest request) {
        EffectiveRubric rubric = SpeechBands.requireRubric(request);
        AzureBandMapping mapping = SpeechBands.mappingOf(rubric);
        Prepared prepared = prepare(request);
        List<AudioChunk> chunks = assessor.prepare(audio.load(request.payload(), request.tenantId()));
        SpeechAssessment assessed = assessor.assess(chunks, "");
        String transcript = assessed.displayText();
        SpeechBands bands = SpeechBands.of(mapping, assessed.accuracy(), assessed.fluency(), assessed.prosody());

        Map<String, Object> detail = AzureScorers.newDetail();
        detail.putAll(prepared.detail());
        bands.writeInto(detail);
        detail.put(AUDIO_DURATION, assessed.audioDurationMs());
        if (!assessed.speechDetected() || transcript.isBlank()) {
            return noSpeech(rubric, request, detail, prepared.lowConfidence());
        }

        TaskRubric task = rubric.task();
        GeminiRequest call = new GeminiRequest(
                GeminiScorers.system(prepared.examiner() + "\n\n" + RubricPromptBuilder.rubricSection(task)),
                prepared.userText().apply(transcript), RubricSchemaBuilder.speakingSchema(task, detailSchema()),
                prepared.images(), null, task.taskType());
        CollectedTraits<GeminiResponse> collected = TraitScoreCollector.collect(RubricPromptBuilder.llmTraits(task),
                () -> gemini.generate(call), GeminiResponse::content);
        detail.put(SCHEMA_ATTEMPTS, collected.attempts());
        GeminiScorers.recordPrompt(detail, collected.response());

        Map<String, Integer> traits = bands.traitsWithContent(collected.scores().get(RubricTraits.CONTENT));
        int itemScore = RubricScorer.score(rubric, new TraitScoreSet(traits, null)).itemScore();
        JsonNode content = collected.response().content();
        GeminiScorers.copyInto(detail, content, detailSchema().keySet().toArray(String[]::new));
        GeminiScorers.copyInto(detail, content, RubricSchemaBuilder.TRAIT_RATIONALES);
        return GeminiScorers.result(json, GeminiScorers.SPEAKING_PROVIDER,
                GeminiScorers.model(MODEL_PREFIX, collected.response().modelVersion()), itemScore, traits,
                GeminiScorers.text(content, RubricSchemaBuilder.FEEDBACK), transcript, detail,
                prepared.lowConfidence(), request.rubricVersionPublicId());
    }

    /** Nothing was said, so there is nothing for Gemini to judge: every trait 0, a valid item score of 0. */
    private AiScoreResult noSpeech(EffectiveRubric rubric, AiScoringRequest request, Map<String, Object> detail,
                                   boolean lowConfidence) {
        detail.put(NO_SPEECH, true);
        Map<String, Integer> traits = new LinkedHashMap<>();
        rubric.task().traits().forEach(trait -> traits.put(trait.key(), 0));
        int itemScore = RubricScorer.score(rubric, new TraitScoreSet(traits, null)).itemScore();
        return GeminiScorers.result(json, AzureScorers.PROVIDER, AzureScorers.MODEL, itemScore, traits, "", "",
                detail, lowConfidence, request.rubricVersionPublicId());
    }

    /** The source text a task is judged against, or a permanent failure when the item has none. */
    static String requireSource(String text) {
        return GeminiScorers.requireText(text, ScoringConstants.AI_SOURCE_TEXT_EMPTY);
    }
}
