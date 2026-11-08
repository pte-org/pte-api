package com.pte.scoring.internal.vendor.gemini;

/**
 * The model that reads a transcript, an essay or a picture and returns the trait scores as one JSON object. Gemini's
 * own API and any OpenAI-compatible gateway (such as Capstone) both answer it; Azure Speech is a separate client.
 */
public interface StructuredModelClient {

    /** Only the HTTP exchange is retried; a 200 the model got wrong fails the same way every time. */
    GeminiResponse generate(GeminiRequest request);
}
