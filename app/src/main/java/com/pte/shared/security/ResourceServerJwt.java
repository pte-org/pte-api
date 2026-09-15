package com.pte.shared.security;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Resource-server JWT wiring shared by every module's security config. Maps
 * the {@code roles} claim to {@code ROLE_*} authorities so
 * {@code @PreAuthorize("hasRole(...)")} works uniformly.
 */
public final class ResourceServerJwt {

    private static final String ROLE_PREFIX = "ROLE_";

    private ResourceServerJwt() {
    }

    public static JwtAuthenticationConverter rolesConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(SecurityClaims.ROLES);
        authorities.setAuthorityPrefix(ROLE_PREFIX);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
