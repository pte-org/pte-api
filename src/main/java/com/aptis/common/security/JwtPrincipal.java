package com.aptis.common.security;

import java.security.Principal;

public record JwtPrincipal(
        Long userId,
        String userType,
        String role,
        Long tenantId) implements Principal {

    @Override
    public String getName() {
        return userId.toString();
    }
}
