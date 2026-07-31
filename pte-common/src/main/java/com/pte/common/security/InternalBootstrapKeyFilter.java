package com.pte.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Grants the ADDITIONAL {@link InternalServiceAuth#AUTHORITY_INTERNAL_SERVICE_BOOTSTRAP}
 * authority on top of an already-{@link InternalApiKeyFilter}-authenticated
 * request, when the request ALSO carries the separate {@link
 * InternalServiceAuth#BOOTSTRAP_HEADER}. Deliberately requires BOTH keys
 * (defense in depth): the base internal-service key alone is never sufficient
 * to reach an all-tenant bootstrap export mode, and the bootstrap key alone
 * (without the base key having already authenticated the request) grants
 * nothing — this filter only ever upgrades an existing authentication, never
 * creates one. Must be registered AFTER {@link InternalApiKeyFilter} in the
 * filter chain. Key comparison uses {@link MessageDigest#isEqual} (constant-time)
 * rather than {@code String.equals} (code-review finding, fixed).
 */
public class InternalBootstrapKeyFilter extends OncePerRequestFilter {

    private final String expectedBootstrapKey;

    public InternalBootstrapKeyFilter(String expectedBootstrapKey) {
        this.expectedBootstrapKey = expectedBootstrapKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        String provided = request.getHeader(InternalServiceAuth.BOOTSTRAP_HEADER);
        if (current != null && current.isAuthenticated() && provided != null
                && MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expectedBootstrapKey.getBytes(StandardCharsets.UTF_8))) {
            List<GrantedAuthority> authorities = new ArrayList<>(current.getAuthorities());
            authorities.add(new SimpleGrantedAuthority(InternalServiceAuth.AUTHORITY_INTERNAL_SERVICE_BOOTSTRAP));
            var upgraded = new UsernamePasswordAuthenticationToken(current.getPrincipal(), null, authorities);
            SecurityContextHolder.getContext().setAuthentication(upgraded);
        }
        chain.doFilter(request, response);
    }
}
