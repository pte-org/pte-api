package com.pte.identity.internal.config;

import com.pte.shared.security.ResourceServerJwt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * Ported from {@code services/iam}'s {@code SecurityConfig}, minus the
 * {@code /internal/**} service-key filter chain: that boundary authorized
 * cross-service HTTP calls (e.g. admin's now-deleted rebuild export), which
 * don't exist inside a monolith — a call between modules is a Java method
 * call, not an HTTP request with its own trust boundary to defend.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // "/ws/**" (com.pte.proctoring, Phase 09): the STOMP handshake itself is
    // permitted here — authentication happens per-frame inside STOMP
    // (StompAuthChannelInterceptor), not at the HTTP upgrade request, since
    // browser WS clients can't reliably set custom handshake headers.
    private static final List<String> PUBLIC_PATHS = List.of(
            "/auth/login", "/auth/refresh", "/auth/jwks", "/actuator/health", "/actuator/health/**", "/ws/**");

    @Bean
    public SecurityFilterChain jwtFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS.toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(ResourceServerJwt.rolesConverter())));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
