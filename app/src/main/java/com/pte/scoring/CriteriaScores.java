package com.pte.scoring;

import com.pte.scoring.internal.constant.ScoringConstants;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The per-criterion scores stored with an AI result ({@code answer_evaluations.criteria_scores_json} and the practice
 * sub-score column). Two shapes live side by side and are told apart by {@code scale}, never converted:
 * <ul>
 *   <li>legacy: a flat map of criterion to a 0-100 score, {@code scale} and {@code rubricVersion} null;</li>
 *   <li>{@value #SCALE_PEARSON_V1}: {"scale":"PEARSON_V1","rubricVersion":"&lt;uuid&gt;","traits":{"CONTENT":4,..}},
 *   trait keys as in the rubric catalog, values on the Pearson scale of that rubric version.</li>
 * </ul>
 * Reading never fails: anything unreadable or of an unknown scale yields no scores, so a damaged row cannot take a
 * screen down.
 */
public record CriteriaScores(String scale, UUID rubricVersion, Map<String, Integer> traits) {

    public static final String SCALE_PEARSON_V1 = ScoringConstants.CRITERIA_SCALE_PEARSON_V1;

    private static final String SCALE = "scale";
    private static final String RUBRIC_VERSION = "rubricVersion";
    private static final String TRAITS = "traits";
    private static final CriteriaScores NONE = legacy(Map.of());

    public CriteriaScores {
        traits = Collections.unmodifiableMap(new LinkedHashMap<>(traits));
    }

    public static CriteriaScores pearson(UUID rubricVersion, Map<String, Integer> traits) {
        return new CriteriaScores(SCALE_PEARSON_V1, rubricVersion, traits);
    }

    public static CriteriaScores legacy(Map<String, Integer> scores) {
        return new CriteriaScores(null, null, scores);
    }

    /** The shape a scorer's result is stored in: Pearson when it names its rubric version, legacy otherwise. */
    public static CriteriaScores ofResult(UUID rubricVersion, Map<String, Integer> scores) {
        return rubricVersion == null ? legacy(scores) : pearson(rubricVersion, scores);
    }

    public boolean isPearson() {
        return SCALE_PEARSON_V1.equals(scale);
    }

    /** Keys sorted, so the stored JSON is stable whatever map the scorer returned. */
    public String toJson(JsonMapper json) {
        if (!isPearson()) {
            return json.writeValueAsString(new TreeMap<>(traits));
        }
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put(SCALE, scale);
        stored.put(RUBRIC_VERSION, rubricVersion == null ? null : rubricVersion.toString());
        stored.put(TRAITS, new TreeMap<>(traits));
        return json.writeValueAsString(stored);
    }

    public static CriteriaScores read(JsonMapper json, String stored) {
        if (stored == null || stored.isBlank()) {
            return NONE;
        }
        JsonNode root;
        try {
            root = json.readTree(stored);
        } catch (RuntimeException unreadable) {
            return NONE;
        }
        if (root == null || !root.isObject()) {
            return NONE;
        }
        if (!root.has(SCALE)) {
            return legacy(numbers(root, false));
        }
        JsonNode traits = root.get(TRAITS);
        if (!SCALE_PEARSON_V1.equals(root.get(SCALE).asText("")) || traits == null || !traits.isObject()) {
            return NONE;
        }
        return pearson(uuidOrNull(root.get(RUBRIC_VERSION)), numbers(traits, true));
    }

    /** The numeric entries of an object; Pearson traits must be whole numbers, legacy scores are read as before. */
    private static Map<String, Integer> numbers(JsonNode object, boolean wholeOnly) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        object.properties().forEach(entry -> {
            JsonNode value = entry.getValue();
            if (value != null && value.isNumber() && (!wholeOnly || value.isIntegralNumber())) {
                scores.put(entry.getKey(), value.asInt());
            }
        });
        return scores;
    }

    private static UUID uuidOrNull(JsonNode value) {
        if (value == null || !value.isString()) {
            return null;
        }
        try {
            return UUID.fromString(value.asText(""));
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
