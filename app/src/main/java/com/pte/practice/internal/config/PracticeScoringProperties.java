package com.pte.practice.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spend and recovery limits for practice scoring. The redispatch threshold must stay above the scoring
 * job budget and the max age above the redispatch threshold, otherwise a slow but healthy job is
 * re-sent or failed while it is still running.
 */
@ConfigurationProperties(prefix = "practice.scoring")
public class PracticeScoringProperties {

    private int rateLimitPerMinute = 10;
    private int maxRetries = 2;
    private int redispatchAfterSeconds = 100;
    private int maxAgeSeconds = 180;

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getRedispatchAfterSeconds() {
        return redispatchAfterSeconds;
    }

    public void setRedispatchAfterSeconds(int redispatchAfterSeconds) {
        this.redispatchAfterSeconds = redispatchAfterSeconds;
    }

    public int getMaxAgeSeconds() {
        return maxAgeSeconds;
    }

    public void setMaxAgeSeconds(int maxAgeSeconds) {
        this.maxAgeSeconds = maxAgeSeconds;
    }
}
