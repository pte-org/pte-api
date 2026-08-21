package com.pte.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InternalBootstrapKeyFilter} tests, driven through the public
 * {@code doFilter} (from {@code OncePerRequestFilter}) with mocked servlet
 * objects so the real "already filtered" / dispatch-type bookkeeping runs
 * too, not just the protected {@code doFilterInternal} body directly.
 */
class InternalBootstrapKeyFilterTest {

    private static final String EXPECTED_KEY = "correct-bootstrap-key";

    private final InternalBootstrapKeyFilter filter = new InternalBootstrapKeyFilter(EXPECTED_KEY);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noExistingAuthentication_headerIgnored_chainProceedsUnchanged() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(null);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader(InternalServiceAuth.BOOTSTRAP_HEADER)).thenReturn(EXPECTED_KEY);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void unauthenticatedExistingAuthentication_headerIgnored_chainProceedsUnchanged() throws Exception {
        Authentication unauthenticated = new UsernamePasswordAuthenticationToken("principal", "credentials");
        assertThat(unauthenticated.isAuthenticated()).isFalse();
        SecurityContextHolder.getContext().setAuthentication(unauthenticated);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader(InternalServiceAuth.BOOTSTRAP_HEADER)).thenReturn(EXPECTED_KEY);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(unauthenticated);
        verify(chain).doFilter(request, response);
    }

    @Test
    void authenticated_withCorrectBootstrapHeader_upgradesAuthority_keepingOriginalAuthorities() throws Exception {
        List<GrantedAuthority> originalAuthorities = List.of(
                new SimpleGrantedAuthority(InternalServiceAuth.AUTHORITY_INTERNAL_SERVICE));
        Authentication current = new UsernamePasswordAuthenticationToken("service-principal", null, originalAuthorities);
        SecurityContextHolder.getContext().setAuthentication(current);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader(InternalServiceAuth.BOOTSTRAP_HEADER)).thenReturn(EXPECTED_KEY);

        filter.doFilter(request, response, chain);

        Authentication upgraded = SecurityContextHolder.getContext().getAuthentication();
        assertThat(upgraded).isNotSameAs(current);
        assertThat(upgraded.getPrincipal()).isEqualTo("service-principal");
        assertThat(upgraded.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(
                        InternalServiceAuth.AUTHORITY_INTERNAL_SERVICE,
                        InternalServiceAuth.AUTHORITY_INTERNAL_SERVICE_BOOTSTRAP);
        verify(chain).doFilter(request, response);
    }

    @Test
    void authenticated_withMissingBootstrapHeader_leavesAuthenticationUnchanged() throws Exception {
        List<GrantedAuthority> originalAuthorities = List.of(
                new SimpleGrantedAuthority(InternalServiceAuth.AUTHORITY_INTERNAL_SERVICE));
        Authentication current = new UsernamePasswordAuthenticationToken("service-principal", null, originalAuthorities);
        SecurityContextHolder.getContext().setAuthentication(current);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader(InternalServiceAuth.BOOTSTRAP_HEADER)).thenReturn(null);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(current);
        verify(chain).doFilter(request, response);
    }

    @Test
    void authenticated_withWrongBootstrapHeader_leavesAuthenticationUnchanged() throws Exception {
        List<GrantedAuthority> originalAuthorities = List.of(
                new SimpleGrantedAuthority(InternalServiceAuth.AUTHORITY_INTERNAL_SERVICE));
        Authentication current = new UsernamePasswordAuthenticationToken("service-principal", null, originalAuthorities);
        SecurityContextHolder.getContext().setAuthentication(current);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader(InternalServiceAuth.BOOTSTRAP_HEADER)).thenReturn("wrong-key");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(current);
        verify(chain).doFilter(request, response);
    }
}
