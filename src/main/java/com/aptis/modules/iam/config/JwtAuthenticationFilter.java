package com.aptis.modules.iam.config;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.aptis.common.config.PublicApiEndpoints;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.interfaces.JwtOperations;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtOperations jwtService;

    public JwtAuthenticationFilter(JwtOperations jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        authenticateRequest(request);
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PublicApiEndpoints.isPublicEndpoint(request.getRequestURI());
    }

    private void authenticateRequest(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(IamApiConstants.AUTHORIZATION_HEADER);
        if (authorizationHeader == null || !authorizationHeader.startsWith(IamApiConstants.BEARER_PREFIX)) {
            return;
        }

        String token = authorizationHeader.substring(IamApiConstants.BEARER_PREFIX.length());
        try {
            Claims claims = jwtService.validateToken(token);
            JwtPrincipal principal = jwtService.extractPrincipal(claims);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority(principal.role())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            LOGGER.warn("JWT authentication failed: {}", exception.getClass().getSimpleName());
        }
    }
}
