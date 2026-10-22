package com.pte.scoring;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Practice-only Gemini settings. Credentials are supplied by the environment. */
@Validated
@ConfigurationProperties(prefix = "practice.ai")
public class PracticeAiProperties {

    private boolean enabled;
    private String apiKey = "";
    private String model = "gemini-flash-lite-latest";
    private String rubricVersion = "pteprep-rubric-v1";

    @Min(1)
    @Max(120_000)
    private int timeoutMs = 45_000;

    @Min(1)
    @Max(10)
    private int maxAttempts = 3;

    @Min(5)
    @Max(300)
    private int leaseSeconds = 300;

    @Min(100)
    @Max(60_000)
    private long pollDelayMs = 1_000;

    @Min(1)
    @Max(3_600)
    private int maxBackoffSeconds = 120;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    public String getRubricVersion() {
        return rubricVersion;
    }

    public void setRubricVersion(String rubricVersion) {
        this.rubricVersion = rubricVersion;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getLeaseSeconds() {
        return leaseSeconds;
    }

    public void setLeaseSeconds(int leaseSeconds) {
        this.leaseSeconds = leaseSeconds;
    }

    public long getPollDelayMs() {
        return pollDelayMs;
    }

    public void setPollDelayMs(long pollDelayMs) {
        this.pollDelayMs = pollDelayMs;
    }

    public int getMaxBackoffSeconds() {
        return maxBackoffSeconds;
    }

    public void setMaxBackoffSeconds(int maxBackoffSeconds) {
        this.maxBackoffSeconds = maxBackoffSeconds;
    }
}
