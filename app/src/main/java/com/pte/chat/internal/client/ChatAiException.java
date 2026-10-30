package com.pte.chat.internal.client;

/**
 * A failed chat call. Messages carry only a fixed description: never the API key, the question, the
 * history or the provider body, because all of them may hold a student's personal text.
 */
public class ChatAiException extends RuntimeException {

    private final ChatAiFailureReason reason;
    private final ChatAiUsage usage;

    public ChatAiException(ChatAiFailureReason reason, String message) {
        this(reason, message, ChatAiUsage.NONE);
    }

    public ChatAiException(ChatAiFailureReason reason, String message, ChatAiUsage usage) {
        super(message);
        this.reason = reason;
        this.usage = usage == null ? ChatAiUsage.NONE : usage;
    }

    public ChatAiException(ChatAiFailureReason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.usage = ChatAiUsage.NONE;
    }

    public ChatAiFailureReason reason() {
        return reason;
    }

    /** Never null; {@link ChatAiUsage#NONE} when no response was received. */
    public ChatAiUsage usage() {
        return usage;
    }
}
