package com.pte.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Edge authentication: validate every inbound JWT against iam's JWKS and reject
 * unauthenticated traffic before it reaches any service (CODING_STANDARDS_MICROSERVICE.md §6).
 * No business logic — authentication + a health allowlist only.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            // Auth bootstrap: a client has no JWT yet when calling these, and jwks is what
            // lets anyone (this gateway included) verify a JWT's signature in the first place.
            // Refresh/logout authenticate via the refresh token in the body, not a bearer JWT.
            "/api/iam/auth/login", "/api/iam/auth/refresh", "/api/iam/auth/logout", "/api/iam/auth/jwks"
    };

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
