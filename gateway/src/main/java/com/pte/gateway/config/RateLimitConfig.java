package com.pte.gateway.config;

import com.pte.common.security.SecurityClaims;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * Per-tenant rate-limit key (ADR-003 layer 2: noisy-neighbor isolation). Keys the
 * Redis token bucket by the {@code tenant_id} claim so one host cannot starve
 * others. Falls back to a shared "anonymous" bucket when no tenant is present.
 */
@Configuration
public class RateLimitConfig {

    private static final String ANONYMOUS_KEY = "anonymous";

    @Bean
    public KeyResolver tenantKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(token -> resolveTenant(token))
                .defaultIfEmpty(ANONYMOUS_KEY);
    }

    private String resolveTenant(JwtAuthenticationToken token) {
        String tenantId = token.getToken().getClaimAsString(SecurityClaims.TENANT_ID);
        return (tenantId == null || tenantId.isBlank()) ? ANONYMOUS_KEY : tenantId;
    }
}
