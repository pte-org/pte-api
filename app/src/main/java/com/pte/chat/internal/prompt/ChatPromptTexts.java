package com.pte.chat.internal.prompt;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.itembank.PromptTexts;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * The text the chatbot uses: the version of each prompt in use on the AI prompt pages, or the wording the application
 * shipped with when none is stored. The student's message and exercise are never part of it.
 */
@Component
public class ChatPromptTexts {

    public static final String KEY_SYSTEM = "SYSTEM";
    public static final String KEY_REFUSAL_VI = "REFUSAL_VI";
    public static final String KEY_REFUSAL_EN = "REFUSAL_EN";

    private final PromptTexts prompts;

    public ChatPromptTexts(PromptTexts prompts) {
        this.prompts = prompts;
    }

    /** Only the shipped wording, for tests and for a deployment with no stored prompts. */
    public static ChatPromptTexts builtIn() {
        return new ChatPromptTexts(new PromptTexts() {
            @Override
            public Optional<String> activeText(String category, String key) {
                return Optional.empty();
            }

            @Override
            public void seedIfMissing(String category, String key, String title, List<Part> parts) {
                // Nothing is stored.
            }
        });
    }

    public String systemInstruction() {
        return stored(KEY_SYSTEM).orElseGet(ChatPrompts::systemInstruction);
    }

    /** Fixed text for an off-topic question; a Vietnamese question is answered in Vietnamese. */
    public String refusal(String language, String question) {
        boolean vietnamese = ChatPrompts.isVietnamese(question) || ChatConstants.LANGUAGE_VI.equalsIgnoreCase(language);
        return stored(vietnamese ? KEY_REFUSAL_VI : KEY_REFUSAL_EN)
                .orElseGet(() -> ChatPrompts.refusal(vietnamese ? ChatConstants.LANGUAGE_VI : language));
    }

    private Optional<String> stored(String key) {
        return prompts.activeText(PromptTexts.CHATBOT_CATEGORY, key).filter(text -> !text.isBlank());
    }
}
