package com.pte.scoring.internal.vendor.gemini;

/** A recording sent inline with the prompt, so the model hears the candidate itself (practice speaking). */
public record GeminiAudio(String format, byte[] data) {

    public static final String WAV = "wav";

    public String mimeType() {
        return "audio/" + format;
    }

    @Override
    public String toString() {
        return "GeminiAudio[" + format + ", bytes=" + (data == null ? 0 : data.length) + "]";
    }
}
