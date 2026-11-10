package com.pte.scoring.internal.rubric.prompt;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.rubric.catalog.RubricCatalogImpl;
import com.pte.scoring.rubric.RubricCatalog;
import com.pte.scoring.rubric.RubricTraits;
import com.pte.scoring.rubric.TaskRubric;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The response schema asks Gemini only for the AI_LLM traits, each a whole number on its own Pearson scale
 * (variant (b): INTEGER with minimum/maximum, chosen from the Gemini responseSchema documentation), never for Form.
 */
class RubricSchemaBuilderTest {

    private static final RubricCatalog CATALOG = new RubricCatalogImpl();

    @Test
    void eachLlmTraitIsAnIntegerBoundedByItsPearsonScale() {
        Map<String, Object> properties = properties(task(ScoringConstants.TASK_TYPE_WRITE_ESSAY));

        assertThat(properties.get(RubricTraits.CONTENT))
                .isEqualTo(Map.of("type", "INTEGER", "minimum", 0, "maximum", 6));
        assertThat(properties.get(RubricTraits.DEVELOPMENT_STRUCTURE_COHERENCE))
                .isEqualTo(Map.of("type", "INTEGER", "minimum", 0, "maximum", 6));
        assertThat(properties.get(RubricTraits.GRAMMAR))
                .isEqualTo(Map.of("type", "INTEGER", "minimum", 0, "maximum", 2));
        assertThat(properties.get(RubricTraits.SPELLING))
                .isEqualTo(Map.of("type", "INTEGER", "minimum", 0, "maximum", 2));
    }

    @Test
    void theModelIsNeverAskedForForm() {
        for (String taskType : List.of(ScoringConstants.TASK_TYPE_WRITE_ESSAY,
                ScoringConstants.TASK_TYPE_SUMMARIZE_WRITTEN_TEXT, ScoringConstants.TASK_TYPE_SUMMARIZE_SPOKEN_TEXT)) {
            Map<String, Object> schema = RubricSchemaBuilder.responseSchema(task(taskType), List.of());

            assertThat(properties(schema)).as(taskType).doesNotContainKey(RubricTraits.FORM)
                    .doesNotContainKey("form");
            assertThat((List<Object>) schema.get("required")).as(taskType).doesNotContain(RubricTraits.FORM);
        }
    }

    @Test
    void everyFieldIsRequiredInRubricOrderFollowedByFeedbackExtrasAndErrors() {
        Map<String, Object> schema = RubricSchemaBuilder.responseSchema(
                task(ScoringConstants.TASK_TYPE_SUMMARIZE_SPOKEN_TEXT), List.of("covered_points", "missed_points"));

        List<String> expected = List.of(RubricTraits.CONTENT, RubricTraits.GRAMMAR, RubricTraits.VOCABULARY,
                RubricTraits.SPELLING, "feedback", "traitRationales", "covered_points", "missed_points", "errors");
        assertThat(schema).containsEntry("type", "OBJECT");
        assertThat(schema.get("required")).isEqualTo(expected);
        assertThat(schema.get("propertyOrdering")).isEqualTo(expected);
        assertThat(properties(schema).get("covered_points"))
                .isEqualTo(Map.of("type", "ARRAY", "items", Map.of("type", "STRING")));
        assertThat(properties(schema).get("feedback")).isEqualTo(Map.of("type", "STRING"));
    }

    @Test
    void summarizeWrittenTextAsksForContentGrammarAndVocabularyOnly() {
        Map<String, Object> schema = RubricSchemaBuilder.responseSchema(
                task(ScoringConstants.TASK_TYPE_SUMMARIZE_WRITTEN_TEXT), List.of());

        assertThat(schema.get("required")).isEqualTo(List.of(RubricTraits.CONTENT, RubricTraits.GRAMMAR,
                RubricTraits.VOCABULARY, "feedback", "traitRationales", "errors"));
        assertThat(properties(schema).get(RubricTraits.CONTENT))
                .isEqualTo(Map.of("type", "INTEGER", "minimum", 0, "maximum", 4));
    }

    /** Speaking: only CONTENT is asked of the model (Azure scores the rest), then feedback and the task's fields. */
    @Test
    void aSpeakingSchemaAsksOnlyForContentThenFeedbackThenTheTaskFields() {
        TaskRubric describeImage = task(ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE);

        Map<String, Object> schema = RubricSchemaBuilder.speakingSchema(describeImage,
                Map.of("missed_details", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"))));

        assertThat(schema.get("required")).isEqualTo(List.of(RubricTraits.CONTENT, "feedback", "traitRationales", "missed_details"));
        assertThat(properties(schema)).containsEntry(RubricTraits.CONTENT,
                Map.of("type", "INTEGER", "minimum", 0, "maximum", 6))
                .doesNotContainKeys(RubricTraits.PRONUNCIATION, RubricTraits.ORAL_FLUENCY, "errors");
    }

    /** Every AI-scored trait gets a required {reason, evidence} pair; coded and speech-engine traits get none. */
    @Test
    @SuppressWarnings("unchecked")
    void traitRationalesHoldAReasonAndEvidencePerLlmTraitOnly() {
        Map<String, Object> rationales = (Map<String, Object>) properties(task(ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE))
                .get(RubricSchemaBuilder.TRAIT_RATIONALES);
        Map<String, Object> traits = (Map<String, Object>) rationales.get("properties");

        assertThat(traits).containsOnlyKeys(RubricTraits.CONTENT);
        Map<String, Object> content = (Map<String, Object>) traits.get(RubricTraits.CONTENT);
        assertThat(content.get("required")).isEqualTo(List.of("reason", "evidence"));
    }

    private static TaskRubric task(String taskType) {
        return CATALOG.find(taskType).orElseThrow();
    }

    private static Map<String, Object> properties(TaskRubric task) {
        return properties(RubricSchemaBuilder.responseSchema(task, List.of()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Map<String, Object> schema) {
        return (Map<String, Object>) schema.get("properties");
    }
}
