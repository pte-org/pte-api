package com.aptis.modules.iam.dto.response.auth;

public record UserProfileResponse(
        Long id,
        String name,
        String credential,
        String role,
        String userType,
        Long tenantId,
        boolean mustChangePassword) {
}
