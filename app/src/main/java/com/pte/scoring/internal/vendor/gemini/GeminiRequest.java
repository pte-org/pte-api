package com.pte.scoring.internal.vendor.gemini;

import java.util.List;
import java.util.Map;

/**
 * One structured-output call. {@code responseSchema} uses Gemini's OpenAPI
 * subset (type OBJECT, properties, required); images precede the text.
 */
public record GeminiRequest(String systemInstruction, String userText, Map<String, Object> responseSchema,
                            List<GeminiImage> images) {

    public GeminiRequest {
        images = images == null ? List.of() : List.copyOf(images);
    }

    /** Sizes only: the text is a candidate's transcript or essay and must never reach a log. */
    @Override
    public String toString() {
        return "GeminiRequest[textChars=" + (userText == null ? 0 : userText.length()) + ", images=" + images.size()
                + "]";
    }
}
