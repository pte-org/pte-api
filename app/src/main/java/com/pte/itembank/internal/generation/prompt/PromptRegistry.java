package com.pte.itembank.internal.generation.prompt;

import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry of task-type-specific prompts. Loads from
 * resources/question-generation/prompts/{TASK_TYPE}.md and _common-generator.md.
 * Each prompt is versioned and includes placeholders for {cefr}, {topicHint}, {avoidTopics}, {lengthRules}.
 */
@Component
public class PromptRegistry {

    private static final int PROMPT_VERSION = 1;
    private final Map<String, String> prompts = new HashMap<>();
    private String commonPrompt;

    public PromptRegistry() {
        loadPrompts();
    }

    /** Retrieve the system/instruction prompt for a task type. Placeholder substitution happens in generator. */
    public String getPrompt(String taskTypeKey) {
        return prompts.get(taskTypeKey.toUpperCase());
    }

    /** Common instruction shared across all task types. */
    public String getCommonPrompt() {
        return commonPrompt;
    }

    public int getPromptVersion() {
        return PROMPT_VERSION;
    }

    private void loadPrompts() {
        // Load common prompt
        commonPrompt = loadResource("_common-generator.md");

        // Load task-type specific prompts for text (Phase 2), audio (Phase 5), dialogue (Phase 6), and chart (Phase 7) types
        String[] taskTypes = {
                "MC_READING_SINGLE",
                "MC_READING_MULTIPLE",
                "FILL_IN_THE_BLANKS_DROPDOWN",
                "FILL_IN_THE_BLANKS_DRAG_AND_DROP",
                "RE_ORDER_PARAGRAPHS",
                "SUMMARIZE_WRITTEN_TEXT",
                "WRITE_ESSAY",
                "READ_ALOUD",
                "WRITE_FROM_DICTATION",
                "REPEAT_SENTENCE",
                "ANSWER_SHORT_QUESTION",
                "SUMMARIZE_SPOKEN_TEXT",
                "MC_LISTENING_SINGLE",
                "MC_LISTENING_MULTIPLE",
                "HIGHLIGHT_CORRECT_SUMMARY",
                "SELECT_MISSING_WORD",
                "HIGHLIGHT_INCORRECT_WORDS",
                "FILL_IN_THE_BLANKS_TYPE_IN",
                "RE_TELL_LECTURE",
                "SUMMARIZE_GROUP_DISCUSSION",
                "RESPOND_TO_A_SITUATION",
                "DESCRIBE_IMAGE"
        };

        for (String taskType : taskTypes) {
            String filename = taskType + ".md";
            try {
                String content = loadResource(filename);
                prompts.put(taskType, content);
            } catch (Exception e) {
                // Log warning but continue; prompt validation will catch missing prompts
                System.err.println("Warning: failed to load prompt for " + taskType + ": " + e.getMessage());
            }
        }
    }

    private String loadResource(String filename) {
        try {
            String path = "/question-generation/prompts/" + filename;
            byte[] bytes = getClass().getResourceAsStream(path).readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException | NullPointerException e) {
            throw new RuntimeException("Failed to load prompt resource: " + filename, e);
        }
    }
}
