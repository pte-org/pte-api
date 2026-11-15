package com.pte.itembank;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Prompts other modules keep editable through the AI prompt pages and read at the moment they call a model: the study
 * chatbot's (the chat module supplies the text it ships with the first time) and the AI scorers' (seeded from the
 * rubric catalog), so what an AI is sent is always the version shown as in use.
 */
public interface PromptTexts {

    /** The category the chatbot's prompts are listed under on the AI prompt pages. */
    String CHATBOT_CATEGORY = "CHATBOT";

    /** The category of the prompts the AI scorers send. */
    String SCORING_CATEGORY = "SCORING";

    /**
     * A prompt in use: the text a model receives and which stored version it came from.
     *
     * @param versionPublicId the stored version, so a score can name the exact prompt it was given
     */
    record ActivePrompt(String text, int versionNo, UUID versionPublicId) {
    }

    /** One titled part of a prompt; a part with no title is the opening, before the first section. */
    record Part(String title, String text) {
    }

    /** The text of the prompt in use, or empty when there is none. */
    Optional<String> activeText(String category, String key);

    /** The prompt in use with its version, or empty when there is none. */
    Optional<ActivePrompt> active(String category, String key);

    /** Stores the shipped prompt when none exists yet; a prompt that exists is never changed. */
    void seedIfMissing(String category, String key, String title, List<Part> parts);
}
