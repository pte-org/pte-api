package com.pte.identity.internal.config;

import com.pte.shared.security.ResourceServerJwt;
import com.pte.shared.web.RateLimitFilter;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * Ported from {@code services/iam}'s {@code SecurityConfig}, minus the
 * {@code /internal/**} service-key filter chain: that boundary authorized
 * cross-service HTTP calls (e.g. admin's now-deleted rebuild export), which
 * don't exist inside a monolith — a call between modules is a Java method
 * call, not an HTTP request with its own trust boundary to defend.
 *
 * <p>CORS and per-tenant rate limiting were ported in here from {@code
 * gateway/src/main/java/com/pte/gateway/config/SecurityConfig.java} and
 * {@code RateLimitConfig.java} when gateway was removed (plans/modular-
 * monolith gateway-removal) — those two concerns had nowhere else to live
 * once nothing sat in front of this app anymore.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // "/ws/**" (com.pte.proctoring, Phase 09): the STOMP handshake itself is
    // permitted here — authentication happens per-frame inside STOMP
    // (StompAuthChannelInterceptor), not at the HTTP upgrade request, since
    // browser WS clients can't reliably set custom handshake headers.
    //
    // "/applications" (com.pte.billing, plans/quang-tenant-commercialization
    // Phase 2): an org submits its tenant application before it has any
    // account to authenticate with. This is an EXACT path match (no "/**"),
    // and TenantApplicationController maps no other HTTP method there — the
    // admin-only list/approve/reject endpoints live under
    // "/admin/applications" specifically so they never fall inside this rule.
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login", "/api/v1/auth/login-options",
            "/api/v1/auth/refresh", "/api/v1/auth/logout",
            "/actuator/health", "/actuator/health/**", "/ws/**",
            "/api/v1/applications", "/api/v1/plans", "/api/v1/webhooks/payos");

    @Bean
    public SecurityFilterChain jwtFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            ProxyManager<String> rateLimitProxyManager,
            JsonMapper jsonMapper,
            @Value("${rate-limit.per-second:40}") int rateLimitPerSecond,
            @Value("${rate-limit.redeem-per-second:5}") int redeemRateLimitPerSecond) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS.toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(ResourceServerJwt.rolesConverter())))
                .addFilterAfter(
                        new RateLimitFilter(rateLimitProxyManager, jsonMapper, rateLimitPerSecond,
                                // Matched against request.getRequestURI(); Nginx preserves the
                                // versioned /api/v1 path sent by the browser.
                                Map.of("/api/v1/license-code-redemptions", redeemRateLimitPerSecond)),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    // FE apps call this app directly now (no cookies — auth is a Bearer JWT
    // in the Authorization header), so credentials stay disabled and the
    // origin list stays an explicit allowlist, same as gateway's version.
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:3001}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
