package com.pte.identity.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Lifetime of a reset link and how long an expired one is kept before the nightly purge removes it. */
@ConfigurationProperties(prefix = "app.password-reset")
public class PasswordResetProperties {

    private long tokenTtlMinutes = 30;
    private long retentionDays = 7;

    public long getTokenTtlMinutes() {
        return tokenTtlMinutes;
    }

    public void setTokenTtlMinutes(long tokenTtlMinutes) {
        this.tokenTtlMinutes = tokenTtlMinutes;
    }

    public long getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(long retentionDays) {
        this.retentionDays = retentionDays;
    }
}
