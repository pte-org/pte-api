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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates a request as {@code ROLE_INTERNAL_SERVICE} when it carries the
 * shared {@link InternalServiceAuth#HEADER}. Wired into a SEPARATE, narrower
 * {@code SecurityFilterChain} matched to {@code /internal/**} only — never the
 * main JWT chain — so a leaked/guessed key can't authenticate anything outside
 * the explicit internal surface. Key comparison uses {@link MessageDigest#isEqual}
 * (constant-time) rather than {@code String.equals} (code-review finding, fixed)
 * to avoid a timing side-channel on the shared secret.
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
        if (provided != null
                && MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expectedKey.getBytes(StandardCharsets.UTF_8))) {
            var authorities = List.of(new SimpleGrantedAuthority(InternalServiceAuth.AUTHORITY_INTERNAL_SERVICE));
            var auth = new UsernamePasswordAuthenticationToken("internal-service", null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }
}
