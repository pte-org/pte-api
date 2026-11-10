package com.pte.identity.internal.config;

import com.pte.identity.internal.constant.IdentityConstants;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** One validated lifetime policy for JWT claims, login responses and persisted refresh tokens. */
@Validated
@ConfigurationProperties(prefix = "app.auth.tokens")
public class AuthTokenProperties {

    @Positive(message = IdentityConstants.ACCESS_TOKEN_TTL_INVALID)
    private long accessTokenTtlSeconds = IdentityConstants.DEFAULT_ACCESS_TOKEN_TTL_SECONDS;

    @Positive(message = IdentityConstants.REFRESH_TOKEN_TTL_INVALID)
    private long refreshTokenTtlSeconds = IdentityConstants.DEFAULT_REFRESH_TOKEN_TTL_SECONDS;

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) {
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    @AssertTrue(message = IdentityConstants.REFRESH_TOKEN_TTL_ORDER_INVALID)
    public boolean isRefreshTokenLifetimeLongerThanAccessToken() {
        return refreshTokenTtlSeconds > accessTokenTtlSeconds;
    }
}
