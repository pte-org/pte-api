package com.pte.identity;

/** Public token result used by non-password identity entry points. */
public record IdentityTokenResponse(String accessToken, String refreshToken, String tokenType,
        long expiresInSeconds, boolean mustChangePassword) {
}
