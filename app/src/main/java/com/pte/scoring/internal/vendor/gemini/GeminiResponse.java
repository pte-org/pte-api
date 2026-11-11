package com.pte.scoring.internal.vendor.gemini;

import tools.jackson.databind.JsonNode;

/**
 * The model's JSON object, the model version that actually served it (aliases move over time) and, for a call made
 * with a prompt key, the prompt it was sent.
 */
public record GeminiResponse(JsonNode content, String modelVersion, PromptUsed prompt) {

    public GeminiResponse(JsonNode content, String modelVersion) {
        this(content, modelVersion, null);
    }

    public GeminiResponse withPrompt(PromptUsed used) {
        return new GeminiResponse(content, modelVersion, used);
    }

    /** The content holds the model's feedback on a candidate's answer, so it is left out. */
    @Override
    public String toString() {
        return "GeminiResponse[modelVersion=" + modelVersion + "]";
    }
}
