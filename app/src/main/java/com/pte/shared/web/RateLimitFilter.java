package com.pte.shared.web;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Request-rate guard (ADR-003 layer 2: noisy-neighbor isolation) on a Redis-backed Bucket4j token bucket.
 * Registered via {@code SecurityConfig}'s {@code addFilterAfter(..., BearerTokenAuthenticationFilter.class)} so it
 * runs after JWT auth resolves. {@link RateLimitKeys} picks the buckets: one per signed-in user plus a higher
 * ceiling for their tenant, or one per client address for callers who are not signed in.
 *
 * <p>The health endpoints and the PayOS webhook are never limited: the first is the container's liveness probe
 * and the second is authenticated by its signature, and neither may be starved by other traffic.
 *
 * <p>If Redis cannot be reached the request is let through. Losing the limiter for a while is better than
 * answering every request with an error.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    static final String HEALTH_PATH = "/actuator/health";
    static final String PAYOS_WEBHOOK_PATH = "/api/v1/webhooks/payos";
    static final String RETRY_AFTER_SECONDS = "1";
    private static final String PATH_SEPARATOR = "/";
    private static final String LIMIT_EXCEEDED = "Rate limit exceeded. Try again shortly.";
    private static final long WARN_INTERVAL_NANOS = Duration.ofMinutes(1).toNanos();
    private static final long NEVER_WARNED = Long.MIN_VALUE;
    private static final Logger LOG = LoggerFactory.getLogger(RateLimitFilter.class);

    private final ProxyManager<String> proxyManager;
    private final JsonMapper jsonMapper;
    private final RateLimitSettings settings;
    private final AtomicLong lastWarnNanos = new AtomicLong(NEVER_WARNED);

    public RateLimitFilter(ProxyManager<String> proxyManager, JsonMapper jsonMapper, RateLimitSettings settings) {
        this.proxyManager = proxyManager;
        this.jsonMapper = jsonMapper;
        this.settings = settings;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (HEALTH_PATH.equals(uri) || uri.startsWith(HEALTH_PATH + PATH_SEPARATOR)) {
            return true;
        }
        return PAYOS_WEBHOOK_PATH.equals(uri) && HttpMethod.POST.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!withinLimits(request)) {
            respondTooManyRequests(response);
            return;
        }
        chain.doFilter(request, response);
    }

    /** Draws one token from each bucket in order and stops at the first that is empty. */
    private boolean withinLimits(HttpServletRequest request) {
        // Resolved outside the try: only a failing store may switch the limiter off, never a bug in the keys.
        List<RateLimitKeys.Limit> limits = RateLimitKeys.resolve(request, settings);
        try {
            for (RateLimitKeys.Limit limit : limits) {
                if (!proxyManager.getProxy(limit.key(), () -> bucketConfiguration(limit.perSecond()))
                        .tryConsume(1)) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException storeFailure) {
            warnStoreUnavailable(storeFailure);
            return true;
        }
    }

    /** At most one line a minute, so an outage does not flood the log. */
    private void warnStoreUnavailable(RuntimeException storeFailure) {
        long now = System.nanoTime();
        long last = lastWarnNanos.get();
        if ((last == NEVER_WARNED || now - last >= WARN_INTERVAL_NANOS) && lastWarnNanos.compareAndSet(last, now)) {
            LOG.warn("Rate limit store unavailable, requests pass unlimited: {}", storeFailure.toString());
        }
    }

    private BucketConfiguration bucketConfiguration(int perSecond) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(perSecond, Duration.ofSeconds(1)))
                .build();
    }

    private void respondTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(jsonMapper.writeValueAsString(ApiResponse.error(LIMIT_EXCEEDED)));
    }
}
