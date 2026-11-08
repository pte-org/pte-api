package com.pte.itembank;

import java.util.List;
import java.util.Optional;

/**
 * The prompts of the study chatbot, which the chat module keeps editable through the AI prompt pages. The chat module
 * supplies the text it ships with the first time, and reads the version in use whenever it answers.
 */
public interface PromptTexts {

    /** The category the chatbot's prompts are listed under on the AI prompt pages. */
    String CHATBOT_CATEGORY = "CHATBOT";

    /** One titled part of a prompt; a part with no title is the opening, before the first section. */
    record Part(String title, String text) {
    }

    /** The text of the prompt in use, or empty when there is none. */
    Optional<String> activeText(String category, String key);

    /** Stores the shipped prompt when none exists yet; a prompt that exists is never changed. */
    void seedIfMissing(String category, String key, String title, List<Part> parts);
}
