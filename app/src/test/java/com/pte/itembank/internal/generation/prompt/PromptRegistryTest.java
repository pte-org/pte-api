package com.pte.itembank.internal.generation.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptRegistryTest {

    private final PromptRegistry registry = PromptTestRegistry.create();

    @Test
    void loadCommonPrompt_NotNull() {
        assertNotNull(registry.getCommonPrompt());
        assertTrue(registry.getCommonPrompt().length() > 0);
    }

    @Test
    void loadTaskTypePrompts_AllPresent() {
        String[] taskTypes = {
                "MC_READING_SINGLE",
                "MC_READING_MULTIPLE",
                "FILL_IN_THE_BLANKS_DROPDOWN",
                "FILL_IN_THE_BLANKS_DRAG_AND_DROP",
                "RE_ORDER_PARAGRAPHS",
                "SUMMARIZE_WRITTEN_TEXT",
                "WRITE_ESSAY",
                "READ_ALOUD"
        };

        for (String taskType : taskTypes) {
            String prompt = registry.getPrompt(taskType);
            assertNotNull(prompt, "Prompt for " + taskType + " should not be null");
            assertTrue(prompt.length() > 0, "Prompt for " + taskType + " should not be empty");
        }
    }

    @Test
    void promptContainsPlaceholders() {
        String commonPrompt = registry.getCommonPrompt();

        // Common prompt should have placeholders for context variables
        assertTrue(commonPrompt.contains("{cefr}") || commonPrompt.contains("CEFR"),
                "Common prompt should reference CEFR level");
    }

    @Test
    void mcReadingSinglePrompt_ContainsJsonExample() {
        String prompt = registry.getPrompt("MC_READING_SINGLE");
        assertNotNull(prompt);
        // Should contain JSON structure hint or example
        assertTrue(prompt.contains("json") || prompt.contains("JSON") || prompt.contains("{"),
                "MC Reading Single prompt should contain JSON reference");
    }

    @Test
    void promptVersion_Positive() {
        assertTrue(registry.getPromptVersion() > 0);
    }

    @Test
    void unsupportedTaskType_ReturnsNull() {
        String prompt = registry.getPrompt("UNSUPPORTED_TASK_TYPE");
        assertNull(prompt);
    }
}
