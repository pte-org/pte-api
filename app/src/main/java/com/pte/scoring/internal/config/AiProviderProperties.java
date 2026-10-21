package com.pte.scoring.internal.config;

import com.pte.scoring.internal.constant.ScoringConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
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

    @Pattern(regexp = "stub|openai-compatible|azure-gemini", message = ScoringConstants.AI_PROVIDER_INVALID)
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
    private int jobBudgetMs = 90_000;
    private int staleDispatchMinutes = 10;
    @Valid
    private final Azure azure = new Azure();
    @Valid
    private final Gemini gemini = new Gemini();

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

    /** Time one job may spend on vendor calls; a retry wait that does not fit is not taken. */
    @Min(1)
    public int getJobBudgetMs() {
        return jobBudgetMs;
    }

    public void setJobBudgetMs(int jobBudgetMs) {
        this.jobBudgetMs = jobBudgetMs;
    }

    /** An answer still {@code AI_SCORING} this long after dispatch is re-dispatched by the next host request. */
    @Min(1)
    public int getStaleDispatchMinutes() {
        return staleDispatchMinutes;
    }

    public void setStaleDispatchMinutes(int staleDispatchMinutes) {
        this.staleDispatchMinutes = staleDispatchMinutes;
    }

    public Azure getAzure() {
        return azure;
    }

    public Gemini getGemini() {
        return gemini;
    }

    /** Azure Speech short-audio REST, used when provider=azure-gemini. */
    public static class Azure {

        private String speechKey = "";
        private String speechRegion = "";
        private int timeoutMs = 60_000;

        public String getSpeechKey() {
            return speechKey;
        }

        public void setSpeechKey(String speechKey) {
            this.speechKey = speechKey;
        }

        public String getSpeechRegion() {
            return speechRegion;
        }

        public void setSpeechRegion(String speechRegion) {
            this.speechRegion = speechRegion;
        }

        @Min(1)
        @Max(60_000)
        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    /** Gemini native API, used when provider=azure-gemini. */
    public static class Gemini {

        private String apiKey = "";
        private String model = "gemini-flash-lite-latest";
        private int timeoutMs = 60_000;

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

        @Min(1)
        @Max(60_000)
        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }
}
