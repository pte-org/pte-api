package com.aptis.modules.iam.interfaces;

import com.aptis.common.security.JwtPrincipal;

import io.jsonwebtoken.Claims;

public interface JwtOperations {
    String generateToken(Long userId, String userType, String role, Long tenantId);
    Claims validateToken(String token);
    JwtPrincipal extractPrincipal(Claims claims);
    long getExpirationSeconds();
}
