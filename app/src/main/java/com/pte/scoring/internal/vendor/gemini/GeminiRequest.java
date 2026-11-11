package com.pte.scoring.internal.vendor.gemini;

import java.util.List;
import java.util.Map;

/**
 * One structured-output call. {@code responseSchema} uses Gemini's OpenAPI
 * subset (type OBJECT, properties, required); images, then the recording, precede the text.
 * {@code promptKey} names the stored scoring prompt whose version in use replaces {@code systemInstruction}
 * ({@link StoredPromptModelClient}); the given instruction is only what is sent when nothing is stored.
 */
public record GeminiRequest(String systemInstruction, String userText, Map<String, Object> responseSchema,
                            List<GeminiImage> images, GeminiAudio audio, String promptKey) {

    public GeminiRequest {
        images = images == null ? List.of() : List.copyOf(images);
    }

    /** A call without a recording: a transcript, an essay or a picture. */
    public GeminiRequest(String systemInstruction, String userText, Map<String, Object> responseSchema,
                         List<GeminiImage> images) {
        this(systemInstruction, userText, responseSchema, images, null, null);
    }

    /** The same call with another system instruction (the stored prompt in use). */
    public GeminiRequest withSystemInstruction(String instruction) {
        return new GeminiRequest(instruction, userText, responseSchema, images, audio, promptKey);
    }

    /** Sizes only: the text is a candidate's transcript or essay and must never reach a log. */
    @Override
    public String toString() {
        return "GeminiRequest[textChars=" + (userText == null ? 0 : userText.length()) + ", images=" + images.size()
                + ", audio=" + (audio != null) + "]";
    }
}
