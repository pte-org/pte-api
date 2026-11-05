package com.pte.scoring.internal.constant;

import java.util.Set;

/** Error codes, limits and fixed vocabulary of the scoring rubric (Pearson Score Guide 2025). */
public final class RubricConstants {

    public static final String RUBRIC_TRAIT_MISSING = "RUBRIC_TRAIT_MISSING";
    public static final String RUBRIC_TRAIT_OUT_OF_RANGE = "RUBRIC_TRAIT_OUT_OF_RANGE";
    public static final String RUBRIC_TRAIT_UNKNOWN = "RUBRIC_TRAIT_UNKNOWN";
    public static final String RUBRIC_WORD_COUNT_INVALID = "RUBRIC_WORD_COUNT_INVALID";
    public static final String RUBRIC_WEIGHT_INVALID = "RUBRIC_WEIGHT_INVALID";
    public static final String RUBRIC_FORM_BANDS_INVALID = "RUBRIC_FORM_BANDS_INVALID";
    public static final String RUBRIC_TASK_TYPE_UNKNOWN = "RUBRIC_TASK_TYPE_UNKNOWN";
    public static final String RUBRIC_TASK_TYPE_NOT_TRAIT_SCORED = "RUBRIC_TASK_TYPE_NOT_TRAIT_SCORED";

    public static final String RUBRIC_PARAMETERS_TOO_LARGE = "RUBRIC_PARAMETERS_TOO_LARGE";
    public static final String RUBRIC_PARAMETERS_MALFORMED = "RUBRIC_PARAMETERS_MALFORMED";
    public static final String RUBRIC_PARAMETERS_KEY_UNKNOWN = "RUBRIC_PARAMETERS_KEY_UNKNOWN";
    public static final String RUBRIC_PARAMETERS_STRUCTURE_MISMATCH = "RUBRIC_PARAMETERS_STRUCTURE_MISMATCH";
    public static final String RUBRIC_GATE_UNKNOWN = "RUBRIC_GATE_UNKNOWN";
    public static final String RUBRIC_GATE_WEIGHT_ZERO = "RUBRIC_GATE_WEIGHT_ZERO";
    public static final String RUBRIC_WEIGHT_SUM_ZERO = "RUBRIC_WEIGHT_SUM_ZERO";
    public static final String RUBRIC_AZURE_MAPPING_VERSION_INVALID = "RUBRIC_AZURE_MAPPING_VERSION_INVALID";
    public static final String RUBRIC_CATALOG_VERSION_INVALID = "RUBRIC_CATALOG_VERSION_INVALID";
    public static final String RUBRIC_VERSION_NOT_FOUND = "RUBRIC_VERSION_NOT_FOUND";
    public static final String RUBRIC_VERSION_NO_ACTIVE = "RUBRIC_VERSION_NO_ACTIVE";
    public static final String RUBRIC_VERSION_IMMUTABLE = "RUBRIC_VERSION_IMMUTABLE";
    public static final String RUBRIC_VERSION_STATUS_INVALID = "RUBRIC_VERSION_STATUS_INVALID";
    /** A concurrent draft or activation won the race (unique version number / single ACTIVE index). */
    public static final String RUBRIC_VERSION_CONCURRENT_CHANGE = "RUBRIC_VERSION_CONCURRENT_CHANGE";
    public static final String RUBRIC_VERSION_TASK_TYPE_MISSING = "RUBRIC_VERSION_TASK_TYPE_MISSING";
    /** A stored version's trait scales no longer match the code catalog; scoring with it would silently rescale. */
    public static final String RUBRIC_VERSION_SCALE_DRIFT = "RUBRIC_VERSION_SCALE_DRIFT";
    public static final String RUBRIC_REJECTION_REASON_INVALID = "RUBRIC_REJECTION_REASON_INVALID";

    /** Upper bound on trait entries accepted from any caller (simulator, examiner, stored JSON). */
    public static final int MAX_TRAIT_SCORE_ENTRIES = 16;
    public static final int MAX_TASK_TYPE_LENGTH = 128;
    public static final int DIFF_DIGEST_LENGTH = 64;
    public static final int ACTIVATION_MAX_ATTEMPTS = 2;
    public static final String RUBRIC_DIFF_UNAVAILABLE = "RUBRIC_DIFF_UNAVAILABLE";
    public static final String RUBRIC_DIFF_STALE = "RUBRIC_DIFF_STALE";
    public static final String RUBRIC_SELF_APPROVAL_FORBIDDEN = "RUBRIC_SELF_APPROVAL_FORBIDDEN";
    public static final int MAX_SIMULATION_JSON_BYTES = 65536;
    public static final String RUBRIC_TRAIT_COUNT_INVALID = "RUBRIC_TRAIT_COUNT_INVALID";
    public static final String RUBRIC_TEXT_METRICS_REQUIRED = "RUBRIC_TEXT_METRICS_REQUIRED";
    /** Upper bound on the serialized size of one rubric version's parameters. */
    public static final int MAX_PARAMETERS_JSON_BYTES = 65536;

    public static final int MIN_TRAIT_WEIGHT = 0;
    public static final int MAX_TRAIT_WEIGHT = 10;
    /** A per-word trait (Read Aloud Content) weighs each word of the passage by this default. */
    public static final int DEFAULT_PER_WORD_WEIGHT = 1;
    public static final int CATALOG_VERSION = 1;
    public static final int MAX_REJECTION_REASON_LENGTH = 1000;

    /** Azure-to-Pearson band mapping tables that exist in code; a stored version must name one of them. */
    public static final int AZURE_BAND_MAPPING_VERSION_1 = 1;
    public static final Set<Integer> SUPPORTED_AZURE_BAND_MAPPING_VERSIONS = Set.of(AZURE_BAND_MAPPING_VERSION_1);
    public static final String AZURE_MAPPING_UNCALIBRATED_LABEL = "chưa hiệu chỉnh với dữ liệu người chấm";
    /** How the Azure scores feed Pronunciation, shown next to the mapping table. */
    public static final String AZURE_PRONUNCIATION_INPUT_VI = "Phát âm = điểm accuracy (0-100) của Azure.";
    /** How the Azure scores feed Oral Fluency: one rule for every speaking task type, whether prosody exists or not. */
    public static final String AZURE_ORAL_FLUENCY_INPUT_VI = "Độ trôi chảy = trung bình fluency và prosody (0-100) "
            + "của Azure; khi Azure không trả prosody thì chỉ dùng fluency.";

    /**
     * Keys of the JSON stored in {@code scoring_rubric_versions.parameters}:
     * {"azureBandMappingVersion":1,"taskTypes":{"&lt;TASK_TYPE&gt;":{"traitWeights":{..},"traitScales":{..},
     * "formBands":null|[{"minWords":..,"maxWords":..,"band":..}],"gateEnabled":{..}}}}. traitScales stores each
     * trait's maximum as a positive integer, or "PER_WORD" for a per-word scale, so a later catalog change to a scale
     * is detected instead of silently rescaling an old version. The V32 seed, the codec and the validator share this
     * shape.
     */
    public static final String PARAM_AZURE_BAND_MAPPING_VERSION = "azureBandMappingVersion";
    public static final String PARAM_TASK_TYPES = "taskTypes";
    public static final String PARAM_TRAIT_WEIGHTS = "traitWeights";
    public static final String PARAM_TRAIT_SCALES = "traitScales";
    /** The traitScales value of a per-word scale (Read Aloud Content). */
    public static final String PARAM_SCALE_PER_WORD = "PER_WORD";
    public static final String PARAM_FORM_BANDS = "formBands";
    public static final String PARAM_GATE_ENABLED = "gateEnabled";
    public static final String PARAM_BAND_MIN_WORDS = "minWords";
    public static final String PARAM_BAND_MAX_WORDS = "maxWords";
    public static final String PARAM_BAND_SCORE = "band";

    public static final String RUBRIC_API_PATH = "/api/v1/scoring-rubrics";

    public static final String SKILL_SPEAKING = "SPEAKING";
    public static final String SKILL_WRITING = "WRITING";
    public static final String SKILL_READING = "READING";
    public static final String SKILL_LISTENING = "LISTENING";

    public static final String OBJECTIVE_CORRECT_INCORRECT = "CORRECT_INCORRECT";
    public static final String OBJECTIVE_PLUS_MINUS_FLOOR_ZERO = "PLUS_MINUS_FLOOR_ZERO";
    public static final String OBJECTIVE_ADJACENT_PAIRS = "ADJACENT_PAIRS";
    public static final String OBJECTIVE_PER_BLANK = "PER_BLANK";
    public static final String OBJECTIVE_LCS_WORDS = "LCS_WORDS";

    private RubricConstants() {
    }
}
