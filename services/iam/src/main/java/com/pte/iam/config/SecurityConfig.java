package com.pte.iam.config;

import com.pte.common.security.ResourceServerJwt;
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
 * iam is both an auth server (public login/refresh/jwks) and a resource server
 * for its own protected endpoints. Stateless; roles come from the JWT {@code roles}
 * claim and map to {@code ROLE_*} authorities for method security.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/auth/login", "/auth/refresh", "/auth/jwks", "/actuator/health", "/actuator/health/**");

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
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
