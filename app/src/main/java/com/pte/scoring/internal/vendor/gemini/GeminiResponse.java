package com.pte.scoring.internal.vendor.gemini;

import tools.jackson.databind.JsonNode;

/** The model's JSON object, and the model version that actually served it (aliases move over time). */
public record GeminiResponse(JsonNode content, String modelVersion) {

    /** The content holds the model's feedback on a candidate's answer, so it is left out. */
    @Override
    public String toString() {
        return "GeminiResponse[modelVersion=" + modelVersion + "]";
    }
}
