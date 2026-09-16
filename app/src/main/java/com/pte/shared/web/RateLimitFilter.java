package com.pte.shared.web;

import com.pte.shared.security.SecurityClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;

/**
 * Per-tenant request-rate guard (ADR-003 layer 2: noisy-neighbor isolation).
 * Replaces gateway's {@code RequestRateLimiter} + {@code tenantKeyResolver}
 * (plans/modular-monolith gateway-removal) with a fixed-window Redis counter:
 * simpler than porting the token-bucket algorithm gateway used, and "close
 * enough" for its actual purpose here (stop one tenant from starving others,
 * not smooth traffic shaping). Registered via {@code SecurityConfig}'s
 * {@code addFilterAfter(..., BearerTokenAuthenticationFilter.class)} so it
 * runs after JWT auth resolves and can read the {@code tenant_id} claim the
 * same way gateway's key resolver did.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String ANONYMOUS_KEY = "anonymous";
    private static final Duration WINDOW = Duration.ofSeconds(1);

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final int limitPerSecond;

    public RateLimitFilter(StringRedisTemplate redisTemplate, JsonMapper jsonMapper, int limitPerSecond) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.limitPerSecond = limitPerSecond;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String tenantKey = resolveTenantKey();
        if (isOverLimit(tenantKey)) {
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

    private boolean isOverLimit(String tenantKey) {
        long window = System.currentTimeMillis() / WINDOW.toMillis();
        String redisKey = "ratelimit:" + tenantKey + ":" + window;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            // Buffer past the window's own duration so a slow first write
            // can't let the key outlive the window it belongs to on one
            // node but expire early on another under clock drift.
            redisTemplate.expire(redisKey, WINDOW.plusSeconds(1));
        }
        return count != null && count > limitPerSecond;
    }

    private void respondTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.getWriter().write(jsonMapper.writeValueAsString(
                ApiResponse.error("Rate limit exceeded. Try again shortly.")));
    }
}
