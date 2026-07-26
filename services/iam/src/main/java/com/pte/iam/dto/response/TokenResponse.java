package com.pte.iam.dto.response;

/** Auth result returned to the client. The raw refresh token is shown once, never re-fetchable. */
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {

    public static TokenResponse bearer(String accessToken, String refreshToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresInSeconds);
    }
}
