package com.pte.shared.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

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
        converter.setJwtGrantedAuthoritiesConverter(normalizingConverter(authorities));
        return converter;
    }

    private static Converter<Jwt, Collection<GrantedAuthority>> normalizingConverter(
            Converter<Jwt, Collection<GrantedAuthority>> delegate) {
        return jwt -> {
            Collection<GrantedAuthority> source = delegate.convert(jwt);
            Set<GrantedAuthority> normalized = new LinkedHashSet<>();
            if (source != null) {
                source.forEach(authority -> {
                    String value = authority.getAuthority();
                    String role = value.startsWith(ROLE_PREFIX)
                            ? value.substring(ROLE_PREFIX.length()) : value;
                    String normalizedRole = role.trim().toUpperCase(Locale.ROOT);
                    String canonicalRole = SecurityRoles.canonicalize(normalizedRole);
                    if (canonicalRole == null || canonicalRole.isBlank()) {
                        return;
                    }
                    normalized.add(new SimpleGrantedAuthority(ROLE_PREFIX + canonicalRole));
                    if (SecurityRoles.LEGACY_PLATFORM_AUTHOR.equals(normalizedRole)) {
                        normalized.add(new SimpleGrantedAuthority(ROLE_PREFIX + normalizedRole));
                    }
                });
            }
            return normalized;
        };
    }
}
