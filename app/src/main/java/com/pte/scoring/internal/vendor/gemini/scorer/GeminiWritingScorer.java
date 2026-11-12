package com.pte.scoring.internal.vendor.gemini.scorer;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.rubric.prompt.RubricPromptBuilder;
import com.pte.scoring.internal.rubric.prompt.RubricSchemaBuilder;
import com.pte.scoring.internal.rubric.prompt.TraitScoreCollector;
import com.pte.scoring.internal.rubric.prompt.TraitScoreCollector.CollectedTraits;
import com.pte.scoring.internal.vendor.AiAnswerScorer;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.azure.AzureScorers;
import com.pte.scoring.internal.vendor.gemini.FormCheck;
import com.pte.scoring.internal.vendor.gemini.GeminiRequest;
import com.pte.scoring.internal.vendor.gemini.GeminiResponse;
import com.pte.scoring.internal.vendor.gemini.StructuredModelClient;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.FormRule;
import com.pte.scoring.rubric.RubricScorer;
import com.pte.scoring.rubric.RubricTraits;
import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TextMetrics;
import com.pte.scoring.rubric.TraitDefinition;
import com.pte.scoring.rubric.TraitScoreSet;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Written tasks scored through the rubric of the request: Gemini returns only the AI_LLM traits, each a whole number
 * on its Pearson scale (prompt and schema built from the catalog); Form is counted in code and banded by the
 * effective rubric's {@link FormRule#bandFor}; the item score is {@link RubricScorer#score} over those traits, so no
 * scorer averages or rounds anything itself. A blank answer is a valid zero without a model call.
 */
abstract class GeminiWritingScorer implements AiAnswerScorer {

    /** Far above any allowed length (WE tops out near 2,000 characters); stops a huge payload being sent on. */
    static final int MAX_MODEL_CHARS = 20_000;

    private static final String FORM_CHECK = "formCheck";
    private static final String NO_SUBMISSION = "noSubmission";
    private static final String TRUNCATED = "truncatedForModel";
    private static final String SCHEMA_ATTEMPTS = "schemaAttempts";

    private final StructuredModelClient gemini;
    private final JsonMapper json;
    private final String examiner;
    private final String sourceLabel;
    private final String answerLabel;
    private final String ask;
    private final List<String> extraFields;

    /**
     * @param examiner    the examiner role; the catalog's rubric section and the injection guard follow it
     * @param extraFields further string-list fields Gemini returns after its feedback
     */
    GeminiWritingScorer(StructuredModelClient gemini, JsonMapper json, String examiner, String sourceLabel,
                        String answerLabel, String ask, List<String> extraFields) {
        this.gemini = gemini;
        this.json = json;
        this.examiner = examiner;
        this.sourceLabel = sourceLabel;
        this.answerLabel = answerLabel;
        this.ask = ask;
        this.extraFields = List.copyOf(extraFields);
    }

    /** The text the answer is judged against; the item's prompt unless a task keeps it elsewhere. */
    String sourceText(AiScoringRequest request) {
        return request.promptText();
    }

    @Override
    public AiScoreResult score(AiScoringRequest request) {
        if (request.rubric() == null) {
            throw new AiProviderException(ScoringConstants.AI_RUBRIC_MISSING);
        }
        EffectiveRubric rubric = request.rubric();
        String source = GeminiScorers.requireText(sourceText(request), ScoringConstants.AI_SOURCE_TEXT_EMPTY);
        String answer = request.payload() == null ? "" : request.payload();
        FormRule formRule = rubric.effectiveFormRule();
        TextMetrics metrics = FormCheck.measure(answer);
        int form = formRule.bandFor(metrics);
        Map<String, Object> detail = AzureScorers.newDetail();
        detail.put(FORM_CHECK, FormCheck.detail(metrics, formRule, form));
        if (answer.isBlank()) {
            return noSubmission(request, detail);
        }

        boolean truncated = answer.length() > MAX_MODEL_CHARS;
        if (truncated) {
            detail.put(TRUNCATED, true);
        }
        String shown = truncated ? answer.substring(0, MAX_MODEL_CHARS) : answer;
        TaskRubric task = rubric.task();
        GeminiRequest call = new GeminiRequest(
                GeminiScorers.system(examiner + "\n\n" + RubricPromptBuilder.rubricSection(task)),
                GeminiScorers.quoted(sourceLabel, source) + "\n" + GeminiScorers.quoted(answerLabel, shown)
                        + "\n" + ask, RubricSchemaBuilder.responseSchema(task, extraFields), List.of(), null,
                task.taskType());
        CollectedTraits<GeminiResponse> collected = TraitScoreCollector.collect(RubricPromptBuilder.llmTraits(task),
                () -> gemini.generate(call), GeminiResponse::content);
        detail.put(SCHEMA_ATTEMPTS, collected.attempts());
        GeminiScorers.recordPrompt(detail, collected.response());

        Map<String, Integer> traits = traitsInRubricOrder(task, collected.scores(), form);
        int rawScore = RubricScorer.score(rubric, new TraitScoreSet(traits, null)).itemScore();
        JsonNode content = collected.response().content();
        GeminiScorers.copyInto(detail, content, RubricSchemaBuilder.ERRORS, RubricSchemaBuilder.TRAIT_RATIONALES);
        GeminiScorers.copyInto(detail, content, extraFields.toArray(String[]::new));
        return GeminiScorers.result(json, GeminiScorers.WRITING_PROVIDER,
                GeminiScorers.model("", collected.response().modelVersion()), rawScore, traits,
                GeminiScorers.text(content, RubricSchemaBuilder.FEEDBACK), null, detail, false,
                request.rubricVersionPublicId());
    }

    /** Nothing was written, so there is nothing for Gemini to judge: a valid zero on every trait, not a failure. */
    private AiScoreResult noSubmission(AiScoringRequest request, Map<String, Object> detail) {
        detail.put(NO_SUBMISSION, true);
        Map<String, Integer> traits = new LinkedHashMap<>();
        request.rubric().task().traits().forEach(trait -> traits.put(trait.key(), 0));
        int rawScore = RubricScorer.score(request.rubric(), new TraitScoreSet(traits, null)).itemScore();
        return GeminiScorers.result(json, GeminiScorers.WRITING_PROVIDER, null, rawScore, traits, "", null, detail,
                false, request.rubricVersionPublicId());
    }

    /** The model's traits plus the coded Form band, in the Score Guide's trait order. */
    private static Map<String, Integer> traitsInRubricOrder(TaskRubric task, Map<String, Integer> modelTraits,
                                                            int form) {
        Map<String, Integer> traits = new LinkedHashMap<>();
        for (TraitDefinition trait : task.traits()) {
            if (RubricTraits.FORM.equals(trait.key())) {
                traits.put(trait.key(), form);
            } else if (modelTraits.containsKey(trait.key())) {
                traits.put(trait.key(), modelTraits.get(trait.key()));
            }
        }
        return traits;
    }
}
