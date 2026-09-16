package com.pte.scoring.internal.config;

import com.pte.scoring.internal.constant.ScoringConstants;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Deployment-owned settings for the optional AI provider adapter.
 * Credentials deliberately have no source-code default.
 */
@Validated
@ConfigurationProperties(prefix = "scoring.ai")
public class AiProviderProperties {

    @Pattern(regexp = "stub|openai-compatible", message = ScoringConstants.AI_PROVIDER_INVALID)
    private String provider = "stub";
    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey = "";
    private String essayModel = "";
    private String speechModel = "";
    private String audioFormat = "wav";
    private int maxTokens = 800;
    private int mediaUrlTtlSeconds = 120;
    private int connectTimeoutMs = 2_000;
    private int readTimeoutMs = 30_000;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

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

    public String getEssayModel() {
        return essayModel;
    }

    public void setEssayModel(String essayModel) {
        this.essayModel = essayModel;
    }

    public String getSpeechModel() {
        return speechModel;
    }

    public void setSpeechModel(String speechModel) {
        this.speechModel = speechModel;
    }

    public String getAudioFormat() {
        return audioFormat;
    }

    public void setAudioFormat(String audioFormat) {
        this.audioFormat = audioFormat;
    }

    @Min(800)
    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    @Min(1)
    public int getMediaUrlTtlSeconds() {
        return mediaUrlTtlSeconds;
    }

    public void setMediaUrlTtlSeconds(int mediaUrlTtlSeconds) {
        this.mediaUrlTtlSeconds = mediaUrlTtlSeconds;
    }

    @Min(1)
    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    @Min(1)
    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
