package com.pte.chat.promptsuite;

import com.pte.chat.internal.client.ChatTurn;

import java.util.List;

/**
 * One case of {@code chat/prompt-suite-v1.json}: what the student sends, what the model must decide
 * ({@code expectedOnTopic}) and the machine-checkable rules the answer must satisfy.
 */
public record PromptSuiteCase(String id, String group, String question, String taskType, String language,
                              List<ChatTurn> history, boolean expectedOnTopic, PassRule passRule) {

    /**
     * Pass rules; a null or empty field means the rule is not used by the case. {@code reviewNote} is for the
     * human reading the report, it is never evaluated.
     */
    public record PassRule(String answerLanguage, List<String> mustNotStartWithPraise, List<String> mustNotContain,
                           Integer maxWords, List<String> mustContainAny, boolean refusalNotEmpty,
                           String reviewNote) {
    }
}
