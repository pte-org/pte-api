package com.pte.scoring.rubric;

import com.pte.scoring.internal.constant.RubricConstants;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.pte.scoring.rubric.RubricTestSupport.CATALOG;
import static com.pte.scoring.rubric.RubricTestSupport.assertRubricError;
import static com.pte.scoring.rubric.RubricTestSupport.task;
import static org.assertj.core.api.Assertions.assertThat;

/** The catalog declares all 22 standard task types, each traceable to a Pearson Score Guide page. */
class RubricCatalogCoverageTest {

    private static final List<String> TRAIT_SCORED = List.of("READ_ALOUD", "REPEAT_SENTENCE", "DESCRIBE_IMAGE",
            "RE_TELL_LECTURE", "SUMMARIZE_GROUP_DISCUSSION", "RESPOND_TO_A_SITUATION", "ANSWER_SHORT_QUESTION",
            "SUMMARIZE_WRITTEN_TEXT", "WRITE_ESSAY", "SUMMARIZE_SPOKEN_TEXT");

    private static final Map<String, String> OBJECTIVE_RULES = Map.ofEntries(
            Map.entry("MC_READING_SINGLE", "CORRECT_INCORRECT"),
            Map.entry("MC_LISTENING_SINGLE", "CORRECT_INCORRECT"),
            Map.entry("HIGHLIGHT_CORRECT_SUMMARY", "CORRECT_INCORRECT"),
            Map.entry("SELECT_MISSING_WORD", "CORRECT_INCORRECT"),
            Map.entry("MC_READING_MULTIPLE", "PLUS_MINUS_FLOOR_ZERO"),
            Map.entry("MC_LISTENING_MULTIPLE", "PLUS_MINUS_FLOOR_ZERO"),
            Map.entry("HIGHLIGHT_INCORRECT_WORDS", "PLUS_MINUS_FLOOR_ZERO"),
            Map.entry("RE_ORDER_PARAGRAPHS", "ADJACENT_PAIRS"),
            Map.entry("FILL_IN_THE_BLANKS_DRAG_AND_DROP", "PER_BLANK"),
            Map.entry("FILL_IN_THE_BLANKS_DROPDOWN", "PER_BLANK"),
            Map.entry("FILL_IN_THE_BLANKS_TYPE_IN", "PER_BLANK"),
            Map.entry("WRITE_FROM_DICTATION", "LCS_WORDS"));

    /** trait key to fixed maximum, in declaration order; null max is the per-word scale. */
    private static Map<String, Integer> speaking(Integer content) {
        Map<String, Integer> traits = new LinkedHashMap<>();
        traits.put(RubricTraits.CONTENT, content);
        traits.put(RubricTraits.PRONUNCIATION, 5);
        traits.put(RubricTraits.ORAL_FLUENCY, 5);
        return traits;
    }

    private static Map<String, Map<String, Integer>> expectedTraits() {
        Map<String, Map<String, Integer>> expected = new LinkedHashMap<>();
        expected.put("READ_ALOUD", speaking(null));
        expected.put("REPEAT_SENTENCE", speaking(3));
        for (String type : List.of("DESCRIBE_IMAGE", "RE_TELL_LECTURE", "SUMMARIZE_GROUP_DISCUSSION",
                "RESPOND_TO_A_SITUATION")) {
            expected.put(type, speaking(6));
        }
        expected.put("ANSWER_SHORT_QUESTION", new LinkedHashMap<>(Map.of(RubricTraits.VOCABULARY, 1)));
        Map<String, Integer> swt = new LinkedHashMap<>();
        swt.put(RubricTraits.CONTENT, 4);
        swt.put(RubricTraits.FORM, 1);
        swt.put(RubricTraits.GRAMMAR, 2);
        swt.put(RubricTraits.VOCABULARY, 2);
        expected.put("SUMMARIZE_WRITTEN_TEXT", swt);
        Map<String, Integer> essay = new LinkedHashMap<>();
        essay.put(RubricTraits.CONTENT, 6);
        essay.put(RubricTraits.FORM, 2);
        essay.put(RubricTraits.DEVELOPMENT_STRUCTURE_COHERENCE, 6);
        essay.put(RubricTraits.GRAMMAR, 2);
        essay.put(RubricTraits.GENERAL_LINGUISTIC_RANGE, 6);
        essay.put(RubricTraits.VOCABULARY_RANGE, 2);
        essay.put(RubricTraits.SPELLING, 2);
        expected.put("WRITE_ESSAY", essay);
        Map<String, Integer> sst = new LinkedHashMap<>();
        sst.put(RubricTraits.CONTENT, 4);
        sst.put(RubricTraits.FORM, 2);
        sst.put(RubricTraits.GRAMMAR, 2);
        sst.put(RubricTraits.VOCABULARY, 2);
        sst.put(RubricTraits.SPELLING, 2);
        expected.put("SUMMARIZE_SPOKEN_TEXT", sst);
        return expected;
    }

    @Test
    void allTwentyTwoStandardTaskTypesHaveARubric() {
        Set<String> expected = new HashSet<>(TRAIT_SCORED);
        expected.addAll(OBJECTIVE_RULES.keySet());

        assertThat(expected).hasSize(22);
        assertThat(CATALOG.all()).hasSize(22);
        assertThat(CATALOG.all()).extracting(TaskRubric::taskType).containsExactlyInAnyOrderElementsOf(expected);
        for (String type : expected) {
            assertThat(CATALOG.find(type)).as(type).isPresent();
        }
    }

    @Test
    void personalIntroductionAndUnknownTypesAreRejected() {
        assertThat(CATALOG.find("PERSONAL_INTRODUCTION")).isEmpty();
        assertThat(CATALOG.find("NO_SUCH_TASK")).isEmpty();
        assertRubricError(() -> CATALOG.defaultParameters("PERSONAL_INTRODUCTION"),
                RubricConstants.RUBRIC_TASK_TYPE_UNKNOWN);
        assertRubricError(() -> CATALOG.defaultParameters("NO_SUCH_TASK"), RubricConstants.RUBRIC_TASK_TYPE_UNKNOWN);
    }

    @Test
    void catalogVersionIsOne() {
        assertThat(CATALOG.catalogVersion()).isEqualTo(1);
    }

    @Test
    void everyTaskHasSkills() {
        for (TaskRubric rubric : CATALOG.all()) {
            assertThat(rubric.skills()).as(rubric.taskType() + " skills").isNotEmpty()
                    .allSatisfy(skill -> assertThat(skill).isNotBlank());
        }
    }

    @Test
    void traitKeysScalesAndOrderMatchTheScoreGuide() {
        expectedTraits().forEach((type, expected) -> {
            TaskRubric rubric = task(type);
            assertThat(rubric.isObjective()).as(type).isFalse();
            assertThat(rubric.traits()).extracting(TraitDefinition::key).as(type)
                    .containsExactlyElementsOf(expected.keySet());
            expected.forEach((key, max) -> {
                TraitScale scale = rubric.trait(key).orElseThrow().scale();
                if (max == null) {
                    assertThat(scale.isPerWord()).as(type + "/" + key).isTrue();
                } else {
                    assertThat(scale.isPerWord()).as(type + "/" + key).isFalse();
                    assertThat(scale.maxFor(null)).as(type + "/" + key).isEqualTo(max);
                }
            });
        });
    }

    @Test
    void fixedMaximaTotalTheDocumentedScores() {
        Map<String, Integer> totals = Map.of("DESCRIBE_IMAGE", 16, "RE_TELL_LECTURE", 16,
                "RESPOND_TO_A_SITUATION", 16, "SUMMARIZE_GROUP_DISCUSSION", 16, "WRITE_ESSAY", 26,
                "SUMMARIZE_WRITTEN_TEXT", 9, "SUMMARIZE_SPOKEN_TEXT", 12, "REPEAT_SENTENCE", 13,
                "ANSWER_SHORT_QUESTION", 1);
        totals.forEach((type, total) -> assertThat(fixedTotal(task(type))).as(type).isEqualTo(total));
        // Read Aloud: 10 fixed points plus the per-word Content scale (N + 10 overall).
        assertThat(fixedTotal(task("READ_ALOUD"))).isEqualTo(10);
    }

    private static int fixedTotal(TaskRubric rubric) {
        return rubric.traits().stream().filter(t -> !t.scale().isPerWord())
                .mapToInt(t -> t.scale().maxFor(null)).sum();
    }

    @Test
    void gatesAreContentForSpeakingAndContentPlusFormForWriting() {
        for (String type : List.of("DESCRIBE_IMAGE", "RE_TELL_LECTURE", "SUMMARIZE_GROUP_DISCUSSION",
                "RESPOND_TO_A_SITUATION")) {
            assertThat(task(type).gateTraits()).as(type).containsExactlyInAnyOrder(RubricTraits.CONTENT);
        }
        for (String type : List.of("SUMMARIZE_WRITTEN_TEXT", "WRITE_ESSAY", "SUMMARIZE_SPOKEN_TEXT")) {
            assertThat(task(type).gateTraits()).as(type)
                    .containsExactlyInAnyOrder(RubricTraits.CONTENT, RubricTraits.FORM);
        }
        for (String type : List.of("READ_ALOUD", "REPEAT_SENTENCE", "ANSWER_SHORT_QUESTION")) {
            assertThat(task(type).gateTraits()).as(type).isEmpty();
        }
    }

    @Test
    void traitSourcesFollowTheScoringPipeline() {
        assertThat(source("READ_ALOUD", RubricTraits.CONTENT)).isEqualTo(TraitSource.MECHANICAL);
        assertThat(source("REPEAT_SENTENCE", RubricTraits.CONTENT)).isEqualTo(TraitSource.MECHANICAL);
        assertThat(source("ANSWER_SHORT_QUESTION", RubricTraits.VOCABULARY)).isEqualTo(TraitSource.MECHANICAL);
        for (String type : List.of("READ_ALOUD", "REPEAT_SENTENCE", "DESCRIBE_IMAGE", "RE_TELL_LECTURE",
                "SUMMARIZE_GROUP_DISCUSSION", "RESPOND_TO_A_SITUATION")) {
            assertThat(source(type, RubricTraits.PRONUNCIATION)).as(type).isEqualTo(TraitSource.AZURE);
            assertThat(source(type, RubricTraits.ORAL_FLUENCY)).as(type).isEqualTo(TraitSource.AZURE);
        }
        for (String type : List.of("DESCRIBE_IMAGE", "RE_TELL_LECTURE", "SUMMARIZE_GROUP_DISCUSSION",
                "RESPOND_TO_A_SITUATION", "SUMMARIZE_WRITTEN_TEXT", "WRITE_ESSAY", "SUMMARIZE_SPOKEN_TEXT")) {
            assertThat(source(type, RubricTraits.CONTENT)).as(type).isEqualTo(TraitSource.AI_LLM);
        }
        for (String type : List.of("SUMMARIZE_WRITTEN_TEXT", "WRITE_ESSAY", "SUMMARIZE_SPOKEN_TEXT")) {
            assertThat(source(type, RubricTraits.FORM)).as(type).isEqualTo(TraitSource.MECHANICAL);
            assertThat(source(type, RubricTraits.GRAMMAR)).as(type).isEqualTo(TraitSource.AI_LLM);
        }
    }

    private static TraitSource source(String type, String key) {
        return task(type).trait(key).orElseThrow().source();
    }

    @Test
    void everyTraitHasNamesAndALevelDescriptorForEveryLevel() {
        for (String type : TRAIT_SCORED) {
            for (TraitDefinition trait : task(type).traits()) {
                String where = type + "/" + trait.key();
                assertThat(trait.displayNameVi()).as(where).isNotBlank();
                assertThat(trait.displayNameEn()).as(where).isNotBlank();
                assertThat(trait.source()).as(where).isNotNull();
                assertThat(trait.levels()).as(where).isNotEmpty();
                trait.levels().forEach(level -> assertThat(level.text()).as(where).isNotBlank());
                if (!trait.scale().isPerWord()) {
                    List<Integer> levels = new ArrayList<>();
                    trait.levels().forEach(level -> levels.add(level.level()));
                    int max = trait.scale().maxFor(null);
                    assertThat(levels).as(where + " levels 0.." + max)
                            .containsExactlyInAnyOrderElementsOf(java.util.stream.IntStream.rangeClosed(0, max)
                                    .boxed().toList());
                }
            }
        }
    }

    @Test
    void formRulesExistExactlyForTheThreeWritingTasks() {
        for (TaskRubric rubric : CATALOG.all()) {
            boolean writing = Set.of("SUMMARIZE_WRITTEN_TEXT", "WRITE_ESSAY", "SUMMARIZE_SPOKEN_TEXT")
                    .contains(rubric.taskType());
            assertThat(rubric.formRule() != null).as(rubric.taskType()).isEqualTo(writing);
        }
    }

    @Test
    void objectiveTasksCarryARuleAndNoTraits() {
        OBJECTIVE_RULES.forEach((type, code) -> {
            TaskRubric rubric = task(type);
            assertThat(rubric.isObjective()).as(type).isTrue();
            assertThat(rubric.traits()).as(type).isEmpty();
            assertThat(rubric.gateTraits()).as(type).isEmpty();
            assertThat(rubric.formRule()).as(type).isNull();
            assertThat(rubric.objectiveRule()).as(type).isNotNull();
            assertThat(rubric.objectiveRule().code()).as(type).isEqualTo(code);
            assertThat(rubric.objectiveRule().descriptionVi()).as(type).isNotBlank();
        });
        for (String type : TRAIT_SCORED) {
            assertThat(task(type).objectiveRule()).as(type).isNull();
        }
    }

    @Test
    void defaultParametersWeighFixedTraitsByTheirMaximumAndPerWordTraitsByOne() {
        expectedTraits().forEach((type, expected) -> {
            RubricParameters parameters = CATALOG.defaultParameters(type);
            expected.forEach((key, max) -> assertThat(parameters.traitWeights().get(key)).as(type + "/" + key)
                    .isEqualTo(max == null ? 1 : max));
            assertThat(parameters.traitWeights().keySet()).as(type).containsExactlyInAnyOrderElementsOf(
                    expected.keySet());
            task(type).gateTraits().forEach(gate -> assertThat(parameters.gateEnabled().get(gate))
                    .as(type + "/" + gate).isTrue());
            assertThat(parameters.azureMappingVersion()).as(type).isNull();
        });
    }

    @Test
    void defaultFormBandsAreTheCatalogBands() {
        for (String type : List.of("SUMMARIZE_WRITTEN_TEXT", "WRITE_ESSAY", "SUMMARIZE_SPOKEN_TEXT")) {
            assertThat(CATALOG.defaultParameters(type).formBands()).as(type)
                    .containsExactlyInAnyOrderElementsOf(task(type).formRule().bands());
        }
    }

    @Test
    void errorCodesAndLimitsAreDeclaredInRubricConstants() {
        assertThat(RubricConstants.RUBRIC_TRAIT_MISSING).isEqualTo("RUBRIC_TRAIT_MISSING");
        assertThat(RubricConstants.RUBRIC_TRAIT_OUT_OF_RANGE).isEqualTo("RUBRIC_TRAIT_OUT_OF_RANGE");
        assertThat(RubricConstants.RUBRIC_TRAIT_UNKNOWN).isEqualTo("RUBRIC_TRAIT_UNKNOWN");
        assertThat(RubricConstants.RUBRIC_WORD_COUNT_INVALID).isEqualTo("RUBRIC_WORD_COUNT_INVALID");
        assertThat(RubricConstants.RUBRIC_WEIGHT_INVALID).isEqualTo("RUBRIC_WEIGHT_INVALID");
        assertThat(RubricConstants.RUBRIC_TASK_TYPE_UNKNOWN).isEqualTo("RUBRIC_TASK_TYPE_UNKNOWN");
        assertThat(RubricConstants.RUBRIC_TASK_TYPE_NOT_TRAIT_SCORED).isEqualTo("RUBRIC_TASK_TYPE_NOT_TRAIT_SCORED");
        assertThat(RubricConstants.MAX_TRAIT_SCORE_ENTRIES).isEqualTo(16);
        assertThat(RubricConstants.MAX_PARAMETERS_JSON_BYTES).isEqualTo(65536);
    }
}
