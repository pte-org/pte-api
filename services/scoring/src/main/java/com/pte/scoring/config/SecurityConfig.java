package com.pte.scoring.config;

import com.pte.common.security.InternalApiKeyFilter;
import com.pte.common.security.InternalBootstrapKeyFilter;
import com.pte.common.security.InternalServiceAuth;
import com.pte.common.security.ResourceServerJwt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Two chains: {@code /internal/**} (service-to-service, API-key — scoring
 * gains its first internal endpoint in rabbitmq-outbox-migration Phase 9, the
 * scored-answer export used by reporting's read-model rebuild) evaluated
 * first, then the normal JWT resource-server chain (host review of AI-scored
 * essays). See authoring's {@code SecurityConfig} for the identical base
 * pattern; the bootstrap filter is new here (Phase 9).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {"/actuator/health", "/actuator/health/**"};

    @Bean
    @Order(1)
    public SecurityFilterChain internalFilterChain(HttpSecurity http,
                                                    @Value("${internal.service-key}") String serviceKey,
                                                    @Value("${internal.bootstrap-key}") String bootstrapKey) throws Exception {
        http
                .securityMatcher(InternalServiceAuth.INTERNAL_PATH_PREFIX)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole(InternalServiceAuth.ROLE_INTERNAL_SERVICE))
                .addFilterBefore(new InternalApiKeyFilter(serviceKey), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new InternalBootstrapKeyFilter(bootstrapKey), InternalApiKeyFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain jwtFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(ResourceServerJwt.rolesConverter())));
        return http.build();
    }
}
