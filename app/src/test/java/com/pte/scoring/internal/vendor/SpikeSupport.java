package com.pte.scoring.internal.vendor;

import com.pte.scoring.internal.vendor.azure.AzureSpeechRestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/** What the real-vendor spike tests share: one HTTP stack, the vendor endpoints and the env-var keys. */
final class SpikeSupport {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    static final Duration TIMEOUT = Duration.ofSeconds(60);
    static final URI GEMINI_BASE = URI.create("https://generativelanguage.googleapis.com");

    private SpikeSupport() {
    }

    static AzureSpeechRestClient azureSpeechClient() {
        return new AzureSpeechRestClient(HTTP, JSON,
                URI.create("https://" + env("SCORING_AI_AZURE_SPEECH_REGION") + ".stt.speech.microsoft.com"),
                env("SCORING_AI_AZURE_SPEECH_KEY"), TIMEOUT);
    }

    static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Spike needs env var " + name);
        }
        return value;
    }
}
