package com.pte.scoring.internal.vendor.gemini.scorer;

import com.pte.scoring.internal.vendor.gemini.FormCheck;
import com.pte.scoring.internal.vendor.gemini.GeminiRequest;
import com.pte.scoring.internal.vendor.gemini.GeminiResponse;
import com.pte.scoring.internal.vendor.gemini.GeminiRestClient;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiAnswerScorer;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.azure.AzureScorers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Written tasks: Gemini scores the language criteria and the code checks the
 * length. The raw score is the mean of the criteria. Where the model also
 * judges Form (structure), a failed length check still zeroes it, because a
 * model cannot count words.
 */
abstract class GeminiWritingScorer implements AiAnswerScorer {

    static final String FORM = "form";
    /** Far above any allowed length (WE tops out near 2,000 characters); stops a huge payload being sent on. */
    static final int MAX_MODEL_CHARS = 20_000;

    private static final Map<String, Object> ERROR_LIST = GeminiScorers.array(GeminiScorers.object(
            "type", GeminiScorers.string(), "text", GeminiScorers.string(), "suggestion", GeminiScorers.string()));

    private final GeminiRestClient gemini;
    private final JsonMapper json;
    private final String examiner;
    private final String sourceLabel;
    private final String answerLabel;
    private final String ask;
    private final List<String> criteria;
    private final boolean modelScoresForm;
    private final List<String> extraFields;
    private final Map<String, Object> schema;

    /**
     * @param criteria        score fields in rubric order; {@value #FORM} is part of it for every task
     * @param modelScoresForm whether Gemini judges Form too, or the length check alone decides it
     * @param extraFields     further string-list fields Gemini returns after its feedback
     */
    GeminiWritingScorer(GeminiRestClient gemini, JsonMapper json, String examiner, String sourceLabel,
                        String answerLabel, String ask, List<String> criteria, boolean modelScoresForm,
                        List<String> extraFields) {
        this.gemini = gemini;
        this.json = json;
        this.examiner = examiner;
        this.sourceLabel = sourceLabel;
        this.answerLabel = answerLabel;
        this.ask = ask;
        this.criteria = criteria;
        this.modelScoresForm = modelScoresForm;
        this.extraFields = extraFields;
        this.schema = buildSchema();
    }

    abstract FormCheck formCheck(String answer, AiScoringRequest request);

    /** The text the answer is judged against; the item's prompt unless a task keeps it elsewhere. */
    String sourceText(AiScoringRequest request) {
        return request.promptText();
    }

    @Override
    public AiScoreResult score(AiScoringRequest request) {
        String source = GeminiScorers.requireText(sourceText(request), ScoringConstants.AI_SOURCE_TEXT_EMPTY);
        String answer = request.payload() == null ? "" : request.payload();
        FormCheck form = formCheck(answer, request);
        Map<String, Object> detail = AzureScorers.newDetail();
        detail.put("formCheck", form.detail());
        if (answer.isBlank()) {
            return noSubmission(detail);
        }

        boolean truncated = answer.length() > MAX_MODEL_CHARS;
        if (truncated) {
            detail.put("truncatedForModel", true);
        }
        String shown = truncated ? answer.substring(0, MAX_MODEL_CHARS) : answer;
        GeminiResponse response = gemini.generate(new GeminiRequest(GeminiScorers.system(examiner),
                GeminiScorers.quoted(sourceLabel, source) + "\n" + GeminiScorers.quoted(answerLabel, shown)
                        + "\n" + ask, schema, List.of()));
        JsonNode content = response.content();

        Map<String, Integer> subScores = new LinkedHashMap<>();
        for (String field : criteria) {
            if (!FORM.equals(field)) {
                subScores.put(field.toUpperCase(Locale.ROOT), GeminiScorers.score(content, field));
            } else if (modelScoresForm) {
                int modelForm = GeminiScorers.score(content, FORM);
                detail.put("modelForm", modelForm);
                subScores.put(field.toUpperCase(Locale.ROOT), form.passed() ? modelForm : 0);
            } else {
                subScores.put(field.toUpperCase(Locale.ROOT), form.score());
            }
        }
        int rawScore = AzureScorers.points(subScores.values().stream().mapToInt(Integer::intValue).average()
                .orElse(0));
        GeminiScorers.copyInto(detail, content, "errors");
        GeminiScorers.copyInto(detail, content, extraFields.toArray(String[]::new));
        return GeminiScorers.result(json, GeminiScorers.WRITING_PROVIDER, GeminiScorers.model("", response.modelVersion()), rawScore,
                subScores, GeminiScorers.text(content, "feedback"), null, detail, false);
    }

    /** Nothing was written, so there is nothing for Gemini to judge: a valid zero, not a failure. */
    private AiScoreResult noSubmission(Map<String, Object> detail) {
        detail.put("noSubmission", true);
        Map<String, Integer> subScores = new LinkedHashMap<>();
        criteria.forEach(field -> subScores.put(field.toUpperCase(Locale.ROOT), 0));
        return GeminiScorers.result(json, GeminiScorers.WRITING_PROVIDER, null, 0, subScores, "", null, detail,
                false);
    }

    private Map<String, Object> buildSchema() {
        List<Object> fields = new ArrayList<>();
        for (String field : criteria) {
            if (!FORM.equals(field) || modelScoresForm) {
                fields.add(field);
                fields.add(GeminiScorers.score());
            }
        }
        fields.add("feedback");
        fields.add(GeminiScorers.string());
        for (String extra : extraFields) {
            fields.add(extra);
            fields.add(GeminiScorers.strings());
        }
        fields.add("errors");
        fields.add(ERROR_LIST);
        return GeminiScorers.object(fields.toArray());
    }
}
