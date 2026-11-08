package com.pte.chat.internal.prompt;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.itembank.PromptTexts;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Puts the chatbot's prompts the application ships with on the AI prompt pages the first time it starts, so they can
 * be edited there. A prompt that is already stored is never changed by a later start.
 */
@Component
public class ChatPromptSeeder implements ApplicationRunner {

    private static final String TITLE_SYSTEM = "Chatbot instructions";
    private static final String TITLE_REFUSAL_VI = "Reply to an off-topic question (Vietnamese)";
    private static final String TITLE_REFUSAL_EN = "Reply to an off-topic question (English)";
    private static final String NO_TITLE = "";

    private final PromptTexts prompts;

    public ChatPromptSeeder(PromptTexts prompts) {
        this.prompts = prompts;
    }

    @Override
    public void run(ApplicationArguments args) {
        prompts.seedIfMissing(PromptTexts.CHATBOT_CATEGORY, ChatPromptTexts.KEY_SYSTEM, TITLE_SYSTEM,
                ChatPrompts.systemParts());
        prompts.seedIfMissing(PromptTexts.CHATBOT_CATEGORY, ChatPromptTexts.KEY_REFUSAL_VI, TITLE_REFUSAL_VI,
                List.of(new PromptTexts.Part(NO_TITLE, ChatPrompts.refusal(ChatConstants.LANGUAGE_VI))));
        prompts.seedIfMissing(PromptTexts.CHATBOT_CATEGORY, ChatPromptTexts.KEY_REFUSAL_EN, TITLE_REFUSAL_EN,
                List.of(new PromptTexts.Part(NO_TITLE, ChatPrompts.refusal(ChatConstants.LANGUAGE_EN))));
    }
}
