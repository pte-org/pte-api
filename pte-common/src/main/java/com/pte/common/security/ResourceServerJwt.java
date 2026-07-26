package com.pte.common.security;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Shared resource-server JWT wiring. Maps the {@code roles} claim (SecurityClaims
 * contract) to {@code ROLE_*} authorities so {@code @PreAuthorize("hasRole(...)")}
 * works uniformly across every service that validates iam-issued tokens.
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
