package com.aptis.modules.iam.service.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.constant.IamMessageConstants;
import com.aptis.modules.iam.interfaces.JwtOperations;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService implements JwtOperations {

    private final SecretKey secretKey;
    private final long expirationSeconds;

    public JwtService(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.expiration-seconds}") long expirationSeconds) {
        this.secretKey = createSecretKey(jwtSecret);
        this.expirationSeconds = expirationSeconds;
    }

    @Override
    public String generateToken(
            Long userId,
            String userType,
            String role,
            Long tenantId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(expirationSeconds);

        return Jwts.builder()
                .subject(userId.toString())
                .claim(IamApiConstants.JWT_CLAIM_ROLE, role)
                .claim(IamApiConstants.JWT_CLAIM_USER_TYPE, userType)
                .claim(IamApiConstants.JWT_CLAIM_TENANT_ID, tenantId)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Override
    public JwtPrincipal extractPrincipal(Claims claims) {
        return new JwtPrincipal(
                Long.valueOf(claims.getSubject()),
                claims.get(IamApiConstants.JWT_CLAIM_USER_TYPE, String.class),
                claims.get(IamApiConstants.JWT_CLAIM_ROLE, String.class),
                extractTenantId(claims));
    }

    @Override
    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    private SecretKey createSecretKey(String jwtSecret) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(IamMessageConstants.JWT_SECRET_MISSING);
        }

        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < IamApiConstants.JWT_MIN_SECRET_BYTES) {
            throw new IllegalStateException(IamMessageConstants.JWT_SECRET_TOO_SHORT);
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }

    private Long extractTenantId(Claims claims) {
        Number tenantId = claims.get(IamApiConstants.JWT_CLAIM_TENANT_ID, Number.class);
        if (tenantId == null) {
            return null;
        }
        return tenantId.longValue();
    }
}
