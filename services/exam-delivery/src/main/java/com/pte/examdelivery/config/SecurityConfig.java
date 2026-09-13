package com.pte.examdelivery.config;

import com.pte.common.security.InternalApiKeyFilter;
import com.pte.common.security.InternalBootstrapKeyFilter;
import com.pte.common.security.InternalServiceAuth;
import com.pte.common.security.ResourceServerJwt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Base chains: {@code /internal/**} (service-to-service, API-key — exam-delivery
 * gains its first provider-side internal endpoints in rabbitmq-outbox-migration
 * Phase 9, the attempt/answer export used by reporting's read-model rebuild)
 * evaluated first, then the normal JWT resource-server chain. When the
 * {@code mock-exam-delivery} profile is active, an additional permit-all local
 * mock chain is inserted between them. See authoring's {@code SecurityConfig}
 * for the identical base pattern; the bootstrap filter is new here (Phase 9).
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
    @Profile("mock-exam-delivery")
    public SecurityFilterChain mockExamFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource mockExamCorsConfigurationSource) throws Exception {
        http
                .securityMatcher("/mock-attempts/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(mockExamCorsConfigurationSource))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Profile("mock-exam-delivery")
    public CorsConfigurationSource mockExamCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(java.util.List.of(
                "http://localhost:8080",
                "http://127.0.0.1:8080"));
        configuration.setAllowedMethods(java.util.List.of(
                HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(java.util.List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    @Order(3)
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
