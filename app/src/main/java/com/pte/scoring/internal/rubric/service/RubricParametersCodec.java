package com.pte.scoring.internal.rubric.service;

import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.exception.RubricVersionException;
import com.pte.scoring.rubric.FormBand;
import com.pte.scoring.rubric.RubricParameters;
import com.pte.scoring.rubric.TraitScale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one place that reads and writes the JSON stored in {@code scoring_rubric_versions.parameters} (shape documented
 * on {@link RubricConstants#PARAM_TASK_TYPES} and in V32). Decoding is strict: every key must be known, every task
 * entry must carry all four keys (formBands may be an explicit null), weights and bands must be JSON integers, gates
 * JSON booleans, and each trait scale a positive JSON integer or exactly {@link RubricConstants#PARAM_SCALE_PER_WORD}.
 * Semantic checks against the catalog belong to {@link RubricParametersValidator}.
 */
@Component
public class RubricParametersCodec {

    private static final List<String> ROOT_KEYS =
            List.of(RubricConstants.PARAM_AZURE_BAND_MAPPING_VERSION, RubricConstants.PARAM_TASK_TYPES);
    private static final List<String> TASK_KEYS = List.of(RubricConstants.PARAM_TRAIT_WEIGHTS,
            RubricConstants.PARAM_TRAIT_SCALES, RubricConstants.PARAM_FORM_BANDS, RubricConstants.PARAM_GATE_ENABLED);
    private static final List<String> BAND_KEYS = List.of(RubricConstants.PARAM_BAND_MIN_WORDS,
            RubricConstants.PARAM_BAND_MAX_WORDS, RubricConstants.PARAM_BAND_SCORE);

    private final JsonMapper jsonMapper;

    public RubricParametersCodec(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public StoredRubricParameters decode(String json) {
        JsonNode root = parse(json);
        requireExactKeys(root, ROOT_KEYS);
        JsonNode azure = root.get(RubricConstants.PARAM_AZURE_BAND_MAPPING_VERSION);
        if (!azure.isInt()) {
            throw invalid(RubricConstants.RUBRIC_AZURE_MAPPING_VERSION_INVALID);
        }
        int azureVersion = azure.intValue();
        JsonNode tasks = root.get(RubricConstants.PARAM_TASK_TYPES);
        requireObject(tasks);
        Map<String, RubricParameters> decoded = new LinkedHashMap<>();
        Map<String, Map<String, TraitScale>> scales = new LinkedHashMap<>();
        tasks.properties().forEach(entry -> {
            decoded.put(entry.getKey(), decodeTask(entry.getValue(), String.valueOf(azureVersion)));
            scales.put(entry.getKey(), decodeScales(entry.getValue().get(RubricConstants.PARAM_TRAIT_SCALES)));
        });
        return new StoredRubricParameters(azureVersion, decoded, scales);
    }

    /** Task types are written in the map's iteration order; callers pass the catalog order for stable output. */
    public String encode(StoredRubricParameters parameters) {
        ObjectNode root = jsonMapper.createObjectNode();
        root.put(RubricConstants.PARAM_AZURE_BAND_MAPPING_VERSION, parameters.azureBandMappingVersion());
        ObjectNode tasks = root.putObject(RubricConstants.PARAM_TASK_TYPES);
        parameters.taskTypes().forEach((taskType, task) -> encodeTask(tasks.putObject(taskType), task,
                parameters.traitScales().getOrDefault(taskType, Map.of())));
        return jsonMapper.writeValueAsString(root);
    }

    private JsonNode parse(String json) {
        if (json == null) {
            throw invalid(RubricConstants.RUBRIC_PARAMETERS_MALFORMED);
        }
        try {
            return jsonMapper.readTree(json);
        } catch (JacksonException malformed) {
            throw invalid(RubricConstants.RUBRIC_PARAMETERS_MALFORMED);
        }
    }

    private RubricParameters decodeTask(JsonNode task, String azureVersion) {
        requireExactKeys(task, TASK_KEYS);
        Map<String, Integer> weights = new LinkedHashMap<>();
        JsonNode weightsNode = task.get(RubricConstants.PARAM_TRAIT_WEIGHTS);
        requireObject(weightsNode);
        requireTraitLimit(weightsNode);
        weightsNode.properties().forEach(entry -> weights.put(entry.getKey(), integer(entry.getValue())));
        Map<String, Boolean> gates = new LinkedHashMap<>();
        JsonNode gatesNode = task.get(RubricConstants.PARAM_GATE_ENABLED);
        requireObject(gatesNode);
        requireTraitLimit(gatesNode);
        gatesNode.properties().forEach(entry -> gates.put(entry.getKey(), bool(entry.getValue())));
        return new RubricParameters(weights, decodeBands(task.get(RubricConstants.PARAM_FORM_BANDS)), gates,
                azureVersion);
    }

    private static Map<String, TraitScale> decodeScales(JsonNode scalesNode) {
        requireObject(scalesNode);
        requireTraitLimit(scalesNode);
        Map<String, TraitScale> scales = new LinkedHashMap<>();
        scalesNode.properties().forEach(entry -> scales.put(entry.getKey(), scale(entry.getValue())));
        return scales;
    }

    private static void requireTraitLimit(JsonNode traits) {
        if (traits.size() > RubricConstants.MAX_TRAIT_SCORE_ENTRIES) {
            throw invalid(RubricConstants.RUBRIC_TRAIT_COUNT_INVALID);
        }
    }

    private static TraitScale scale(JsonNode node) {
        if (node.isString() && RubricConstants.PARAM_SCALE_PER_WORD.equals(node.asString())) {
            return TraitScale.perWord();
        }
        int max = integer(node);
        if (max < 1) {
            throw invalid(RubricConstants.RUBRIC_PARAMETERS_MALFORMED);
        }
        return TraitScale.fixed(max);
    }

    private List<FormBand> decodeBands(JsonNode bands) {
        if (bands.isNull()) {
            return null;
        }
        if (!bands.isArray()) {
            throw invalid(RubricConstants.RUBRIC_PARAMETERS_MALFORMED);
        }
        List<FormBand> decoded = new ArrayList<>();
        for (int index = 0; index < bands.size(); index++) {
            JsonNode band = bands.get(index);
            requireExactKeys(band, BAND_KEYS);
            decoded.add(new FormBand(integer(band.get(RubricConstants.PARAM_BAND_MIN_WORDS)),
                    integer(band.get(RubricConstants.PARAM_BAND_MAX_WORDS)),
                    integer(band.get(RubricConstants.PARAM_BAND_SCORE))));
        }
        return decoded;
    }

    private void encodeTask(ObjectNode node, RubricParameters task, Map<String, TraitScale> traitScales) {
        ObjectNode weights = node.putObject(RubricConstants.PARAM_TRAIT_WEIGHTS);
        task.traitWeights().forEach(weights::put);
        ObjectNode scales = node.putObject(RubricConstants.PARAM_TRAIT_SCALES);
        traitScales.forEach((trait, scale) -> {
            if (scale.isPerWord()) {
                scales.put(trait, RubricConstants.PARAM_SCALE_PER_WORD);
            } else {
                scales.put(trait, scale.fixedMax());
            }
        });
        if (task.formBands() == null) {
            node.putNull(RubricConstants.PARAM_FORM_BANDS);
        } else {
            ArrayNode bands = node.putArray(RubricConstants.PARAM_FORM_BANDS);
            task.formBands().forEach(band -> bands.addObject()
                    .put(RubricConstants.PARAM_BAND_MIN_WORDS, band.minWords())
                    .put(RubricConstants.PARAM_BAND_MAX_WORDS, band.maxWords())
                    .put(RubricConstants.PARAM_BAND_SCORE, band.band()));
        }
        ObjectNode gates = node.putObject(RubricConstants.PARAM_GATE_ENABLED);
        task.gateEnabled().forEach(gates::put);
    }

    /** Unknown keys are reported before missing ones; missing keys are checked in the listed order. */
    private static void requireExactKeys(JsonNode node, List<String> expected) {
        requireObject(node);
        for (String key : node.propertyNames()) {
            if (!expected.contains(key)) {
                throw invalid(RubricConstants.RUBRIC_PARAMETERS_KEY_UNKNOWN);
            }
        }
        for (String key : expected) {
            if (!node.has(key)) {
                throw invalid(expectedMissingCode(key));
            }
        }
    }

    private static String expectedMissingCode(String key) {
        return RubricConstants.PARAM_AZURE_BAND_MAPPING_VERSION.equals(key)
                ? RubricConstants.RUBRIC_AZURE_MAPPING_VERSION_INVALID
                : RubricConstants.RUBRIC_PARAMETERS_MALFORMED;
    }

    private static void requireObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw invalid(RubricConstants.RUBRIC_PARAMETERS_MALFORMED);
        }
    }

    private static int integer(JsonNode node) {
        if (!node.isInt()) {
            throw invalid(RubricConstants.RUBRIC_PARAMETERS_MALFORMED);
        }
        return node.intValue();
    }

    private static boolean bool(JsonNode node) {
        if (!node.isBoolean()) {
            throw invalid(RubricConstants.RUBRIC_PARAMETERS_MALFORMED);
        }
        return node.booleanValue();
    }

    private static RubricVersionException invalid(String code) {
        return new RubricVersionException(HttpStatus.BAD_REQUEST, code);
    }
}
