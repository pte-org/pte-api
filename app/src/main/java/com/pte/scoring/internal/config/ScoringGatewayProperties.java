package com.pte.scoring.internal.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * {@code scoring.ai.gateway.*}: the OpenAI-compatible {@code /chat/completions} service (Capstone) that reads
 * transcripts, essays and pictures when {@code scoring.ai.text-vendor=gateway}. Azure Speech still scores the audio.
 */
public class ScoringGatewayProperties {

    private String baseUrl = "";
    private String apiKey = "";
    private String model = "";
    private int timeoutMs = 60_000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    /** Longest wait for one scoring call to the gateway. */
    @Min(1)
    @Max(120_000)
    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
