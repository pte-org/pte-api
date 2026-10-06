package com.pte.billing.internal.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Applies non-cacheable response headers to reveal success and error responses. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LicenseCodeRevealNoStoreFilter extends OncePerRequestFilter {

    private static final String REVEAL_PREFIX = "/api/v1/admin/license-codes/";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (isRevealRequest(request)) {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Pragma", "no-cache");
        }
        filterChain.doFilter(request, response);
    }

    private boolean isRevealRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().startsWith(REVEAL_PREFIX)
                && request.getRequestURI().endsWith("/reveal");
    }
}
