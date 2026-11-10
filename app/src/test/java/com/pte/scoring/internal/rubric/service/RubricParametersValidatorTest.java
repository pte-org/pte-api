package com.pte.scoring.internal.rubric.service;

import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.rubric.RubricTraits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.CATALOG;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.CODEC;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.child;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.defaultsJson;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.editRoot;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.editTask;
import static org.assertj.core.api.Assertions.assertThat;

/** Every rule the validator enforces before a rubric version's parameters are stored. */
class RubricParametersValidatorTest {

    private static final String DI = "DESCRIBE_IMAGE";
    private static final String WE = "WRITE_ESSAY";
    private static final int CATALOG_VERSION = RubricConstants.CATALOG_VERSION;

    private final RubricParametersValidator validator = new RubricParametersValidator(CATALOG, CODEC);

    @Test
    void theCatalogDefaultsForAll22TaskTypesAreValid() {
        StoredRubricParameters stored = validator.validate(defaultsJson(), CATALOG_VERSION);

        assertThat(stored.taskTypes()).hasSize(22);
        assertThat(stored.azureBandMappingVersion()).isEqualTo(RubricConstants.AZURE_BAND_MAPPING_VERSION_1);
    }

    @Test
    void jsonAboveTheByteLimitIsRejectedBeforeParsing() {
        String padded = defaultsJson() + " ".repeat(RubricConstants.MAX_PARAMETERS_JSON_BYTES);

        rejects(padded, HttpStatus.BAD_REQUEST, RubricConstants.RUBRIC_PARAMETERS_TOO_LARGE);
    }

    @Test
    void theLimitCountsUtf8BytesNotCharacters() {
        String multiByte = "ữ".repeat(RubricConstants.MAX_PARAMETERS_JSON_BYTES / 2);

        rejects(multiByte, HttpStatus.BAD_REQUEST, RubricConstants.RUBRIC_PARAMETERS_TOO_LARGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "[]", "null", "\"text\""})
    void malformedJsonIsRejected(String json) {
        rejects(json, RubricConstants.RUBRIC_PARAMETERS_MALFORMED);
    }

    @Test
    void nullParametersAreMalformed() {
        rejects(null, RubricConstants.RUBRIC_PARAMETERS_MALFORMED);
    }

    @Test
    void anUnknownTaskTypeIsRejected() {
        rejects(editRoot(root -> child(root, RubricConstants.PARAM_TASK_TYPES)
                .set("PERSONAL_INTRODUCTION", child(root, RubricConstants.PARAM_TASK_TYPES).get(DI))),
                RubricConstants.RUBRIC_TASK_TYPE_UNKNOWN);
    }

    @Test
    void aMissingTaskTypeIsAStructureMismatch() {
        rejects(editRoot(root -> child(root, RubricConstants.PARAM_TASK_TYPES).remove(DI)),
                RubricConstants.RUBRIC_PARAMETERS_STRUCTURE_MISMATCH);
    }

    @Test
    void unknownKeysAtRootOrTaskLevelAreRejected() {
        rejects(editRoot(root -> root.put("overrides", 1)), RubricConstants.RUBRIC_PARAMETERS_KEY_UNKNOWN);
        rejects(editTask(DI, task -> task.put("prompt", "x")), RubricConstants.RUBRIC_PARAMETERS_KEY_UNKNOWN);
    }

    @Test
    void aTaskEntryMustCarryEveryKeyEvenWhenNull() {
        rejects(editTask(DI, task -> task.remove(RubricConstants.PARAM_FORM_BANDS)),
                RubricConstants.RUBRIC_PARAMETERS_MALFORMED);
    }

    @Test
    void anUnknownTraitIsRejectedAndAMissingOneIsAStructureMismatch() {
        rejects(editTask(DI, task -> child(task, RubricConstants.PARAM_TRAIT_WEIGHTS).put(RubricTraits.FORM, 1)),
                RubricConstants.RUBRIC_TRAIT_UNKNOWN);
        rejects(editTask(DI, task -> child(task, RubricConstants.PARAM_TRAIT_WEIGHTS)
                .remove(RubricTraits.ORAL_FLUENCY)), RubricConstants.RUBRIC_PARAMETERS_STRUCTURE_MISMATCH);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 11, 100})
    void weightsOutside0To10AreRejected(int weight) {
        rejects(weight(DI, RubricTraits.PRONUNCIATION, weight), RubricConstants.RUBRIC_WEIGHT_INVALID);
    }

    @Test
    void weightsAtTheBoundsAreAccepted() {
        validator.validate(weight(DI, RubricTraits.PRONUNCIATION, 0), CATALOG_VERSION);
        validator.validate(weight(DI, RubricTraits.PRONUNCIATION, 10), CATALOG_VERSION);
    }

    @Test
    void aNonIntegerWeightIsMalformed() {
        rejects(editTask(DI, task -> child(task, RubricConstants.PARAM_TRAIT_WEIGHTS)
                .put(RubricTraits.CONTENT, 2.5)), RubricConstants.RUBRIC_PARAMETERS_MALFORMED);
    }

    @Test
    void aCatalogGateMustKeepPositiveWeightEvenWhenDisabled() {
        rejects(weight(DI, RubricTraits.CONTENT, 0), RubricConstants.RUBRIC_GATE_WEIGHT_ZERO);

        rejects(editTask(DI, task -> {
            child(task, RubricConstants.PARAM_TRAIT_WEIGHTS).put(RubricTraits.CONTENT, 0);
            child(task, RubricConstants.PARAM_GATE_ENABLED).put(RubricTraits.CONTENT, false);
        }), RubricConstants.RUBRIC_GATE_WEIGHT_ZERO);
    }

    @Test
    void oversizedTraitMapsAreRejectedBeforeTheyAreStored() {
        rejects(editTask(DI, task -> {
            ObjectNode weights = child(task, RubricConstants.PARAM_TRAIT_WEIGHTS);
            for (int i = 0; i <= RubricConstants.MAX_TRAIT_SCORE_ENTRIES; i++) weights.put("EXTRA_" + i, 1);
        }), RubricConstants.RUBRIC_TRAIT_COUNT_INVALID);
    }

    @Test
    void weightsSummingTo0AreRejected() {
        rejects(editTask("REPEAT_SENTENCE", task -> {
            ObjectNode weights = child(task, RubricConstants.PARAM_TRAIT_WEIGHTS);
            weights.put(RubricTraits.CONTENT, 0).put(RubricTraits.PRONUNCIATION, 0).put(RubricTraits.ORAL_FLUENCY, 0);
        }), RubricConstants.RUBRIC_WEIGHT_SUM_ZERO);
    }

    @Test
    void gateKeysMustMatchTheCatalogGates() {
        rejects(editTask(DI, task -> child(task, RubricConstants.PARAM_GATE_ENABLED)
                .put(RubricTraits.PRONUNCIATION, true)), RubricConstants.RUBRIC_GATE_UNKNOWN);
        rejects(editTask(DI, task -> child(task, RubricConstants.PARAM_GATE_ENABLED).remove(RubricTraits.CONTENT)),
                RubricConstants.RUBRIC_PARAMETERS_STRUCTURE_MISMATCH);
    }

    @Test
    void editedNonOverlappingBandsWithTheTopBandAreAccepted() {
        validator.validate(bands(WE, new int[][]{{180, 320, 2}, {100, 179, 1}, {321, 400, 1}}), CATALOG_VERSION);
    }

    @Test
    void overlappingBandsAreRejected() {
        rejects(bands(WE, new int[][]{{200, 300, 2}, {250, 350, 1}}), RubricConstants.RUBRIC_FORM_BANDS_INVALID);
    }

    @Test
    void bandsWithoutTheFormMaximumAreRejected() {
        rejects(bands(WE, new int[][]{{200, 300, 1}, {120, 199, 1}}), RubricConstants.RUBRIC_FORM_BANDS_INVALID);
    }

    @Test
    void aBandAboveTheFormScaleIsRejected() {
        rejects(bands(WE, new int[][]{{200, 300, 3}, {120, 199, 2}}), RubricConstants.RUBRIC_FORM_BANDS_INVALID);
    }

    @Test
    void emptyOrInvertedOrNegativeBandsAreRejected() {
        rejects(bands(WE, new int[][]{}), RubricConstants.RUBRIC_FORM_BANDS_INVALID);
        rejects(bands(WE, new int[][]{{300, 200, 2}}), RubricConstants.RUBRIC_FORM_BANDS_INVALID);
        rejects(bands(WE, new int[][]{{-5, 200, 2}}), RubricConstants.RUBRIC_FORM_BANDS_INVALID);
    }

    @Test
    void formBandsMustExistExactlyWhereTheCatalogHasAFormRule() {
        rejects(editTask(WE, task -> task.putNull(RubricConstants.PARAM_FORM_BANDS)),
                RubricConstants.RUBRIC_PARAMETERS_STRUCTURE_MISMATCH);
        rejects(bands(DI, new int[][]{{1, 10, 1}}), RubricConstants.RUBRIC_PARAMETERS_STRUCTURE_MISMATCH);
    }

    @Test
    void aMissingOrUnknownOrNonIntegerAzureMappingVersionIsRejected() {
        String code = RubricConstants.RUBRIC_AZURE_MAPPING_VERSION_INVALID;
        rejects(editRoot(root -> root.remove(RubricConstants.PARAM_AZURE_BAND_MAPPING_VERSION)), code);
        rejects(editRoot(root -> root.put(RubricConstants.PARAM_AZURE_BAND_MAPPING_VERSION, 2)), code);
        rejects(editRoot(root -> root.put(RubricConstants.PARAM_AZURE_BAND_MAPPING_VERSION, "1")), code);
    }

    @Test
    void aStoredScaleMustEqualTheCatalogScale() {
        rejects(scale(DI, RubricTraits.CONTENT, 5), RubricConstants.RUBRIC_PARAMETERS_STRUCTURE_MISMATCH);
        rejects(editTask(DI, task -> child(task, RubricConstants.PARAM_TRAIT_SCALES)
                .put(RubricTraits.CONTENT, RubricConstants.PARAM_SCALE_PER_WORD)),
                RubricConstants.RUBRIC_PARAMETERS_STRUCTURE_MISMATCH);
        rejects(scale("READ_ALOUD", RubricTraits.CONTENT, 1), RubricConstants.RUBRIC_PARAMETERS_STRUCTURE_MISMATCH);
    }

    @Test
    void scaleKeysMustBeExactlyTheCatalogTraits() {
        rejects(scale(DI, RubricTraits.FORM, 2), RubricConstants.RUBRIC_TRAIT_UNKNOWN);
        rejects(editTask(DI, task -> child(task, RubricConstants.PARAM_TRAIT_SCALES).remove(RubricTraits.CONTENT)),
                RubricConstants.RUBRIC_PARAMETERS_STRUCTURE_MISMATCH);
        rejects(editTask(DI, task -> task.remove(RubricConstants.PARAM_TRAIT_SCALES)),
                RubricConstants.RUBRIC_PARAMETERS_MALFORMED);
    }

    @Test
    void aScaleThatIsNeitherAPositiveIntegerNorThePerWordMarkerIsMalformed() {
        rejects(editTask(DI, task -> child(task, RubricConstants.PARAM_TRAIT_SCALES).put(RubricTraits.CONTENT, 6.0)),
                RubricConstants.RUBRIC_PARAMETERS_MALFORMED);
        rejects(editTask(DI, task -> child(task, RubricConstants.PARAM_TRAIT_SCALES)
                .put(RubricTraits.CONTENT, "per_word")), RubricConstants.RUBRIC_PARAMETERS_MALFORMED);
        rejects(editTask(DI, task -> child(task, RubricConstants.PARAM_TRAIT_SCALES).putNull(RubricTraits.CONTENT)),
                RubricConstants.RUBRIC_PARAMETERS_MALFORMED);
        rejects(scale(DI, RubricTraits.CONTENT, 0), RubricConstants.RUBRIC_PARAMETERS_MALFORMED);
    }

    @Test
    void anotherCatalogVersionIsRejectedWhenOnlyATraitScaleDiffers() {
        RubricVersionFixtures.assertRubricVersionError(
                () -> validator.validate(scale(DI, RubricTraits.CONTENT, 7), CATALOG_VERSION + 1),
                HttpStatus.BAD_REQUEST, RubricConstants.RUBRIC_PARAMETERS_STRUCTURE_MISMATCH);
    }

    @Test
    void anotherCatalogVersionIsAcceptedWhenTheStructureMatches() {
        assertThat(validator.validate(defaultsJson(), CATALOG_VERSION + 1).taskTypes()).hasSize(22);
    }

    @Test
    void anotherCatalogVersionIsRejectedWhenTheStructureDiffers() {
        String differentStructure = editTask(DI, task -> child(task, RubricConstants.PARAM_TRAIT_WEIGHTS)
                .put(RubricTraits.GRAMMAR, 1));

        RubricVersionFixtures.assertRubricVersionError(
                () -> validator.validate(differentStructure, CATALOG_VERSION + 1), HttpStatus.BAD_REQUEST,
                RubricConstants.RUBRIC_TRAIT_UNKNOWN);
    }

    @Test
    void aCatalogVersionBelow1IsRejected() {
        RubricVersionFixtures.assertRubricVersionError(() -> validator.validate(defaultsJson(), 0),
                HttpStatus.BAD_REQUEST, RubricConstants.RUBRIC_CATALOG_VERSION_INVALID);
    }

    private static String weight(String taskType, String trait, int weight) {
        return editTask(taskType, task -> child(task, RubricConstants.PARAM_TRAIT_WEIGHTS).put(trait, weight));
    }

    private static String scale(String taskType, String trait, int max) {
        return editTask(taskType, task -> child(task, RubricConstants.PARAM_TRAIT_SCALES).put(trait, max));
    }

    private static String bands(String taskType, int[][] rows) {
        return editTask(taskType, task -> {
            ArrayNode array = task.putArray(RubricConstants.PARAM_FORM_BANDS);
            for (int[] row : rows) {
                array.addObject().put(RubricConstants.PARAM_BAND_MIN_WORDS, row[0])
                        .put(RubricConstants.PARAM_BAND_MAX_WORDS, row[1])
                        .put(RubricConstants.PARAM_BAND_SCORE, row[2]);
            }
        });
    }

    private void rejects(String json, String code) {
        rejects(json, HttpStatus.BAD_REQUEST, code);
    }

    private void rejects(String json, HttpStatus status, String code) {
        RubricVersionFixtures.assertRubricVersionError(() -> validator.validate(json, CATALOG_VERSION), status, code);
    }
}
