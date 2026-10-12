package com.pte.scoring.internal.vendor.gemini;

/** An image sent inline with the prompt (e.g. the Describe Image picture). */
public record GeminiImage(String mimeType, byte[] data) {

    @Override
    public String toString() {
        return "GeminiImage[" + mimeType + ", bytes=" + (data == null ? 0 : data.length) + "]";
    }
}
