package com.pte.tenancy.internal.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Deployment-owned transport and cache settings for the Vietnam location catalog. */
@Validated
@ConfigurationProperties(prefix = "locations.vietnam")
public class VietnamLocationProperties {

    @NotBlank
    private String baseUrl = "https://provinces.open-api.vn/api/v2";

    @Min(1)
    private int connectTimeoutMs = 2_000;

    @Min(1)
    private int readTimeoutMs = 10_000;

    @Min(1)
    private long cacheTtlMs = 86_400_000L;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public long getCacheTtlMs() {
        return cacheTtlMs;
    }

    public void setCacheTtlMs(long cacheTtlMs) {
        this.cacheTtlMs = cacheTtlMs;
    }
}
