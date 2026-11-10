package com.pte.itembank.internal.generation.llm;

/**
 * A provider call failed. Carries only the classification: the message is the stable code, because provider
 * bodies and prompts must never reach logs or the database.
 */
public class LlmProviderException extends RuntimeException {

    private final LlmFailureType type;

    public LlmProviderException(LlmFailureType type) {
        super(type.code());
        this.type = type;
    }

    public LlmFailureType getType() {
        return type;
    }
}
