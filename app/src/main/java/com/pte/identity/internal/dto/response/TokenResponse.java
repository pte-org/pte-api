package com.pte.identity.internal.dto.response;

/** Auth result returned to the client. The raw refresh token is shown once, never re-fetchable. */
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds,
                            boolean mustChangePassword) {

    public static TokenResponse bearer(String accessToken, String refreshToken, long expiresInSeconds) {
        return bearer(accessToken, refreshToken, expiresInSeconds, false);
    }

    public static TokenResponse bearer(String accessToken, String refreshToken, long expiresInSeconds,
                                       boolean mustChangePassword) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresInSeconds, mustChangePassword);
    }
}
