package com.pte.chat.internal.client;

/** Token counts and finish reason of one provider response; kept even for failed calls so cost stays visible. */
public record ChatAiUsage(int tokensIn, int tokensOut, int tokensThinking, String finishReason) {

    /** Used when the failure happened before any response arrived. */
    public static final ChatAiUsage NONE = new ChatAiUsage(0, 0, 0, null);
}
