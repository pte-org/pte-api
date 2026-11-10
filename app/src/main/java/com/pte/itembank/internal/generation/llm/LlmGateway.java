package com.pte.itembank.internal.generation.llm;

/** Chat completion that must return one JSON object, for the generator and the reviewer. */
public interface LlmGateway {

    /**
     * @return the raw JSON text of the assistant message
     * @throws LlmProviderException classified provider failure (never carries the prompt or response body)
     */
    String completeJson(LlmRole role, String systemPrompt, String userPrompt);

    /**
     * The same call with one image in front of the user text, for reviewing a drawn chart. A gateway that cannot send
     * images refuses the call.
     */
    default String completeJsonWithImage(LlmRole role, String systemPrompt, String userPrompt, String mimeType,
                                         byte[] image) {
        throw new LlmProviderException(LlmFailureType.REJECTED);
    }
}
