package com.pte.scoring.internal.rubric.prompt;

import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TraitDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Gemini {@code responseSchema} of a rubric-scored task: one field per AI_LLM trait (never Form), then feedback,
 * any task-specific string lists, then the language errors. Every field is required.
 *
 * <p>Schema variant (b), INTEGER with {@code minimum}/{@code maximum}, chosen by the Gemini REST responseSchema
 * documentation ({@code enum} is documented for STRING only; INTEGER supports {@code minimum}/{@code maximum}); the
 * real-Gemini smoke ({@code RubricSchemaSmokeSpikeTest}) was not executed when this was chosen (no key). The schema
 * is a request to the model, not a guarantee: {@link TraitScoreReader} still validates every value in Java.
 */
public final class RubricSchemaBuilder {

    public static final String FEEDBACK = "feedback";
    public static final String ERRORS = "errors";

    private static final String TYPE = "type";

    private RubricSchemaBuilder() {
    }

    public static Map<String, Object> responseSchema(TaskRubric task, List<String> extraStringListFields) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (TraitDefinition trait : RubricPromptBuilder.llmTraits(task)) {
            properties.put(trait.key(), traitSchema(trait.scale().maxFor(null)));
        }
        properties.put(FEEDBACK, string());
        for (String extra : extraStringListFields) {
            properties.put(extra, array(string()));
        }
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", string());
        error.put("text", string());
        error.put("suggestion", string());
        properties.put(ERRORS, array(object(error)));
        return object(properties);
    }

    /**
     * The schema of a speaking task: one field per AI_LLM trait (Content only: Pronunciation and Oral Fluency come
     * from Azure), then feedback, then the task's own descriptive fields as given (their schemas in order).
     */
    public static Map<String, Object> speakingSchema(TaskRubric task, Map<String, Object> extraProperties) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (TraitDefinition trait : RubricPromptBuilder.llmTraits(task)) {
            properties.put(trait.key(), traitSchema(trait.scale().maxFor(null)));
        }
        properties.put(FEEDBACK, string());
        properties.putAll(extraProperties);
        return object(properties);
    }

    /** Variant (b): a whole number from 0 to the trait's Pearson maximum. */
    static Map<String, Object> traitSchema(int max) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put(TYPE, "INTEGER");
        schema.put("minimum", 0);
        schema.put("maximum", max);
        return schema;
    }

    private static Map<String, Object> string() {
        return Map.of(TYPE, "STRING");
    }

    private static Map<String, Object> array(Map<String, Object> items) {
        return Map.of(TYPE, "ARRAY", "items", items);
    }

    private static Map<String, Object> object(Map<String, Object> properties) {
        List<String> names = new ArrayList<>(properties.keySet());
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put(TYPE, "OBJECT");
        schema.put("properties", properties);
        schema.put("required", names);
        schema.put("propertyOrdering", names);
        return schema;
    }
}
