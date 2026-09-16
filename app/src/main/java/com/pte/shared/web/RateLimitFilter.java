package com.pte.shared.web;

import com.pte.shared.security.SecurityClaims;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Per-tenant request-rate guard (ADR-003 layer 2: noisy-neighbor isolation).
 * Replaces gateway's {@code RequestRateLimiter} + {@code tenantKeyResolver}
 * (plans/modular-monolith gateway-removal) with a Redis-backed Bucket4j
 * token bucket — the same algorithm gateway used, just fronted by this app
 * instead of Spring Cloud Gateway. Registered via {@code SecurityConfig}'s
 * {@code addFilterAfter(..., BearerTokenAuthenticationFilter.class)} so it
 * runs after JWT auth resolves and can read the {@code tenant_id} claim the
 * same way gateway's key resolver did.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String ANONYMOUS_KEY = "anonymous";

    private final ProxyManager<String> proxyManager;
    private final JsonMapper jsonMapper;
    private final int limitPerSecond;

    public RateLimitFilter(ProxyManager<String> proxyManager, JsonMapper jsonMapper, int limitPerSecond) {
        this.proxyManager = proxyManager;
        this.jsonMapper = jsonMapper;
        this.limitPerSecond = limitPerSecond;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Bucket bucket = proxyManager.getProxy(resolveTenantKey(), this::bucketConfiguration);
        if (!bucket.tryConsume(1)) {
            respondTooManyRequests(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private String resolveTenantKey() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String tenantId = jwtAuth.getToken().getClaimAsString(SecurityClaims.TENANT_ID);
            if (tenantId != null && !tenantId.isBlank()) {
                return tenantId;
            }
        }
        return ANONYMOUS_KEY;
    }

    private BucketConfiguration bucketConfiguration() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(limitPerSecond, Duration.ofSeconds(1)))
                .build();
    }

    private void respondTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.getWriter().write(jsonMapper.writeValueAsString(
                ApiResponse.error("Rate limit exceeded. Try again shortly.")));
    }
}
