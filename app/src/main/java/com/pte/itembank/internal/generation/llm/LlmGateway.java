package com.pte.itembank.internal.generation.llm;

/** Chat completion that must return one JSON object, for the generator and the reviewer. */
public interface LlmGateway {

    /**
     * @return the raw JSON text of the assistant message
     * @throws LlmProviderException classified provider failure (never carries the prompt or response body)
     */
    String completeJson(LlmRole role, String systemPrompt, String userPrompt);
}
