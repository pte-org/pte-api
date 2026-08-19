package com.pte.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Core security-boundary tests for {@link InternalExportScope#resolve} —
 * the guard preventing a normal per-tenant export call from silently
 * widening into a cross-tenant (all-tenant) export.
 */
class InternalExportScopeTest {

    @Test
    void requestedTenantIdPresent_returnsItRegardlessOfAuthorities() {
        Authentication authentication = mock(Authentication.class);
        UUID requestedTenantId = UUID.randomUUID();

        UUID resolved = InternalExportScope.resolve(authentication, requestedTenantId);

        assertThat(resolved).isEqualTo(requestedTenantId);
        // Authorities must not even be consulted when a tenantId was explicitly requested.
        verifyNoInteractions(authentication);
    }

    @Test
    void requestedTenantIdNull_withBootstrapAuthority_returnsNull_allTenantsMode() {
        Authentication authentication = mock(Authentication.class);
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority(InternalServiceAuth.AUTHORITY_INTERNAL_SERVICE),
                new SimpleGrantedAuthority(InternalServiceAuth.AUTHORITY_INTERNAL_SERVICE_BOOTSTRAP));
        when(authentication.getAuthorities()).thenAnswer(invocation -> authorities);

        UUID resolved = InternalExportScope.resolve(authentication, null);

        assertThat(resolved).isNull();
    }

    @Test
    void requestedTenantIdNull_withoutBootstrapAuthority_throwsAccessDenied() {
        Authentication authentication = mock(Authentication.class);
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority(InternalServiceAuth.AUTHORITY_INTERNAL_SERVICE));
        when(authentication.getAuthorities()).thenAnswer(invocation -> authorities);

        assertThatThrownBy(() -> InternalExportScope.resolve(authentication, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("tenantId is required");
    }

    @Test
    void requestedTenantIdNull_withNoAuthoritiesAtAll_throwsAccessDenied() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getAuthorities()).thenAnswer(invocation -> List.of());

        assertThatThrownBy(() -> InternalExportScope.resolve(authentication, null))
                .isInstanceOf(AccessDeniedException.class);
    }
}
