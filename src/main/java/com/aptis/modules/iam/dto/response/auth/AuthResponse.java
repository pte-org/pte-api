package com.aptis.modules.iam.dto.response.auth;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        String role,
        String userType,
        Long tenantId,
        boolean mustChangePassword) {
}
