package com.pte.chat.internal.client;

/** A validated model answer. {@code markdown} is empty when {@code onTopic} is false. */
public record ChatAiAnswer(boolean onTopic, String markdown, ChatAiUsage usage, String model) {
}
