package com.pte.shared.web;

import com.pte.shared.security.SecurityClaims;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private static final int USER_LIMIT = 40;
    private static final int TENANT_LIMIT = 200;
    private static final int ANONYMOUS_LIMIT = 10;
    private static final int REDEEM_LIMIT = 5;
    private static final String REDEEM_URI = "/api/v1/license-code-redemptions";
    private static final String PUBLIC_URI = "/api/v1/public/thing";
    private static final String TENANT_A = "tenant-a";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final List<String> requestedKeys = new ArrayList<>();
    private final List<Integer> requestedCapacities = new ArrayList<>();
    private final Set<String> rejectingKeys = new HashSet<>();
    private RuntimeException managerFailure;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        RateLimitSettings settings = new RateLimitSettings(USER_LIMIT, TENANT_LIMIT, ANONYMOUS_LIMIT,
                Map.of(REDEEM_URI, REDEEM_LIMIT));
        filter = new RateLimitFilter(fakeProxyManager(), jsonMapper, settings);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @SuppressWarnings("unchecked")
    private ProxyManager<String> fakeProxyManager() {
        return mock(ProxyManager.class, invocation -> {
            if (!"getProxy".equals(invocation.getMethod().getName())) {
                return null;
            }
            if (managerFailure != null) {
                throw managerFailure;
            }
            String key = invocation.getArgument(0);
            Supplier<BucketConfiguration> configuration = invocation.getArgument(1);
            requestedKeys.add(key);
            requestedCapacities.add((int) configuration.get().getBandwidths()[0].getCapacity());
            BucketProxy bucket = mock(BucketProxy.class);
            when(bucket.tryConsume(anyLong())).thenReturn(!rejectingKeys.contains(key));
            return bucket;
        });
    }

    private void signIn(String subject, String tenantId) {
        Jwt.Builder jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject);
        if (tenantId != null) {
            jwt.claim(SecurityClaims.TENANT_ID, tenantId);
        } else {
            jwt.claim("scope", "x");
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt.build()));
    }

    private MockHttpServletRequest request(String method, String uri, String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    private MockHttpServletResponse run(MockHttpServletRequest request) throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, MockFilterChain chain)
            throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void anonymousCallersFromDifferentAddressesUseDifferentIpBuckets() throws Exception {
        run(request("GET", PUBLIC_URI, "10.0.0.1", null));
        run(request("GET", PUBLIC_URI, "10.0.0.2", null));

        assertThat(requestedKeys).containsExactly("ip:10.0.0.1", "ip:10.0.0.2");
        assertThat(requestedCapacities).containsExactly(ANONYMOUS_LIMIT, ANONYMOUS_LIMIT);
    }

    @Test
    void forwardedForUsesTheLastEntryAndSocketAddressIsTheFallback() throws Exception {
        run(request("GET", PUBLIC_URI, "10.0.0.9", "203.0.113.1, 198.51.100.7"));
        run(request("GET", PUBLIC_URI, "10.0.0.9", null));

        assertThat(requestedKeys).containsExactly("ip:198.51.100.7", "ip:10.0.0.9");
    }

    @Test
    void twoUsersOfOneTenantHaveOwnUserBucketsAndShareOneTenantBucket() throws Exception {
        signIn("user-1", TENANT_A);
        run(request("GET", PUBLIC_URI, "10.0.0.1", null));
        signIn("user-2", TENANT_A);
        run(request("GET", PUBLIC_URI, "10.0.0.1", null));

        assertThat(requestedKeys).containsExactly("user:user-1", "tenant:tenant-a", "user:user-2", "tenant:tenant-a");
        assertThat(requestedCapacities).containsExactly(USER_LIMIT, TENANT_LIMIT, USER_LIMIT, TENANT_LIMIT);
    }

    @Test
    void rejectedUserBucketNeverTouchesTheTenantBucketAndAnswers429() throws Exception {
        rejectingKeys.add("user:user-1");
        signIn("user-1", TENANT_A);
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request("GET", PUBLIC_URI, "10.0.0.1", null), chain);

        assertThat(requestedKeys).containsExactly("user:user-1");
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void rejectedTenantBucketAnswers429AfterUserBucketWasUsed() throws Exception {
        rejectingKeys.add("tenant:tenant-a");
        signIn("user-1", TENANT_A);

        MockHttpServletResponse response = run(request("GET", PUBLIC_URI, "10.0.0.1", null));

        assertThat(requestedKeys).containsExactly("user:user-1", "tenant:tenant-a");
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void jwtWithoutTenantDrawsOnlyFromTheUserBucket() throws Exception {
        signIn("user-1", null);

        run(request("GET", PUBLIC_URI, "10.0.0.1", null));

        assertThat(requestedKeys).containsExactly("user:user-1");
    }

    @Test
    void jwtWithBlankSubjectFallsBackToTheIpBucketWithoutThrowing() {
        signIn("  ", TENANT_A);

        assertThatCode(() -> run(request("GET", PUBLIC_URI, "10.0.0.1", null))).doesNotThrowAnyException();

        assertThat(requestedKeys).containsExactly("ip:10.0.0.1");
        assertThat(requestedCapacities).containsExactly(ANONYMOUS_LIMIT);
    }

    @Test
    void routeWithItsOwnLimitUsesOnlyRouteBucketsUserFirstThenTenant() throws Exception {
        signIn("user-1", TENANT_A);

        run(request("POST", REDEEM_URI, "10.0.0.1", null));

        assertThat(requestedKeys).containsExactly("user:user-1|route:" + REDEEM_URI,
                "tenant:tenant-a|route:" + REDEEM_URI);
        assertThat(requestedCapacities).containsExactly(REDEEM_LIMIT, REDEEM_LIMIT);
    }

    @Test
    void rejectedRouteUserBucketNeverTouchesTheRouteTenantBucket() throws Exception {
        rejectingKeys.add("user:user-1|route:" + REDEEM_URI);
        signIn("user-1", TENANT_A);

        MockHttpServletResponse response = run(request("POST", REDEEM_URI, "10.0.0.1", null));

        assertThat(requestedKeys).containsExactly("user:user-1|route:" + REDEEM_URI);
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void anonymousCallerOnARouteWithItsOwnLimitUsesTheRouteIpBucket() throws Exception {
        run(request("POST", REDEEM_URI, "10.0.0.1", null));

        assertThat(requestedKeys).containsExactly("ip:10.0.0.1|route:" + REDEEM_URI);
        assertThat(requestedCapacities).containsExactly(REDEEM_LIMIT);
    }

    @Test
    void healthEndpointsAndPayOsWebhookPostAreNotFiltered() {
        assertThat(filter.shouldNotFilter(request("GET", "/actuator/health", "10.0.0.1", null))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/actuator/health/liveness", "10.0.0.1", null))).isTrue();
        assertThat(filter.shouldNotFilter(request("POST", "/api/v1/webhooks/payos", "10.0.0.1", null))).isTrue();
    }

    @Test
    void lookAlikePathsAndOtherMethodsAreStillFiltered() {
        assertThat(filter.shouldNotFilter(request("GET", "/actuator/healthx", "10.0.0.1", null))).isFalse();
        assertThat(filter.shouldNotFilter(request("POST", "/api/v1/webhooks/other", "10.0.0.1", null))).isFalse();
        assertThat(filter.shouldNotFilter(request("GET", "/api/v1/webhooks/payos", "10.0.0.1", null))).isFalse();
    }

    @Test
    void rejectedRequestGets429WithRetryAfterJsonErrorBodyAndChainIsNotInvoked() throws Exception {
        rejectingKeys.add("ip:10.0.0.1");
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request("GET", PUBLIC_URI, "10.0.0.1", null), chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).isEqualTo(jsonMapper.writeValueAsString(
                ApiResponse.error("Rate limit exceeded. Try again shortly.")));
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void allowedRequestIsPassedDownTheChain() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request("GET", PUBLIC_URI, "10.0.0.1", null), chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void storeFailureLetsTheRequestThroughWithoutThrowing() {
        managerFailure = new IllegalStateException("redis down");
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse[] response = new MockHttpServletResponse[1];

        assertThatCode(() -> response[0] = run(request("GET", PUBLIC_URI, "10.0.0.1", null), chain))
                .doesNotThrowAnyException();

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response[0].getStatus()).isEqualTo(200);
    }

    @Test
    void tryConsumeFailureLetsTheRequestThroughWithoutThrowing() {
        BucketProxy broken = mock(BucketProxy.class);
        when(broken.tryConsume(anyLong())).thenThrow(new IllegalStateException("redis timeout"));
        @SuppressWarnings("unchecked")
        ProxyManager<String> manager = mock(ProxyManager.class, invocation ->
                "getProxy".equals(invocation.getMethod().getName()) ? broken : null);
        RateLimitFilter brokenFilter = new RateLimitFilter(manager, jsonMapper,
                new RateLimitSettings(USER_LIMIT, TENANT_LIMIT, ANONYMOUS_LIMIT, Map.of()));
        MockFilterChain chain = new MockFilterChain();

        assertThatCode(() -> brokenFilter.doFilter(request("GET", PUBLIC_URI, "10.0.0.1", null),
                new MockHttpServletResponse(), chain)).doesNotThrowAnyException();

        assertThat(chain.getRequest()).isNotNull();
    }
}
