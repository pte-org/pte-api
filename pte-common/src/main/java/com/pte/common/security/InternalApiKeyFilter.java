package com.pte.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates a request as {@code ROLE_INTERNAL_SERVICE} when it carries the
 * shared {@link InternalServiceAuth#HEADER}. Wired into a SEPARATE, narrower
 * {@code SecurityFilterChain} matched to {@code /internal/**} only — never the
 * main JWT chain — so a leaked/guessed key can't authenticate anything outside
 * the explicit internal surface.
 */
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private final String expectedKey;

    public InternalApiKeyFilter(String expectedKey) {
        this.expectedKey = expectedKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(InternalServiceAuth.HEADER);
        if (provided != null && provided.equals(expectedKey)) {
            var authorities = List.of(new SimpleGrantedAuthority(InternalServiceAuth.AUTHORITY_INTERNAL_SERVICE));
            var auth = new UsernamePasswordAuthenticationToken("internal-service", null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }
}
