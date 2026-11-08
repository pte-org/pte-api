package com.pte.itembank.internal.generation.usage;

/** Tokens of one or more provider calls; a count the provider did not report is 0. */
public record GenerationUsage(long calls, long tokensIn, long tokensOut) {

    public static final GenerationUsage NONE = new GenerationUsage(0, 0, 0);

    /** One call's usage from the provider's {@code prompt_tokens} and {@code completion_tokens}; -1 means unknown. */
    public static GenerationUsage ofCall(long promptTokens, long completionTokens) {
        return new GenerationUsage(1, Math.max(promptTokens, 0), Math.max(completionTokens, 0));
    }
}
