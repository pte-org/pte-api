package com.pte.identity.internal.security;

import com.pte.TestClocks;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.config.AuthTokenProperties;
import com.pte.shared.security.SecurityClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The access token carries the account's own organization (only when it has one) and its own roles. */
class AccessTokenIssuerTest {

    private JwtEncoder encoder;
    private AccessTokenIssuer issuer;

    @BeforeEach
    void setUp() {
        encoder = mock(JwtEncoder.class);
        Jwt signed = mock(Jwt.class);
        when(signed.getTokenValue()).thenReturn("signed-fixture");
        when(encoder.encode(any())).thenReturn(signed);
        AuthTokenProperties properties = new AuthTokenProperties();
        properties.setAccessTokenTtlSeconds(900);
        issuer = new AccessTokenIssuer(encoder, properties, TestClocks.fixed());
    }

    private User account(UUID organizationId, Role... roles) {
        User user = new User();
        user.setId(1L);
        user.setPublicId(UUID.randomUUID());
        user.setUsername("fixture");
        user.setStatus(UserStatus.ACTIVE);
        user.setOrganizationId(organizationId);
        user.setRoles(new HashSet<>(Set.of(roles)));
        return user;
    }

    private JwtClaimsSet issuedClaims() {
        ArgumentCaptor<JwtEncoderParameters> encoded = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(encoder).encode(encoded.capture());
        return encoded.getValue().getClaims();
    }

    @Test
    void organizationAccount_carriesItsOwnOrganizationAsTenantId() {
        UUID organizationId = UUID.randomUUID();
        User host = account(organizationId, Role.HOST_ADMIN);

        assertThat(issuer.issue(host)).isEqualTo("signed-fixture");

        JwtClaimsSet claims = issuedClaims();
        assertThat(claims.getClaimAsString(SecurityClaims.TENANT_ID)).isEqualTo(organizationId.toString());
        assertThat(claims.getSubject()).isEqualTo(host.getPublicId().toString());
    }

    @Test
    void freePracticeStudentWithoutOrganization_getsNoTenantIdClaim() {
        issuer.issue(account(null, Role.STUDENT));

        JwtClaimsSet claims = issuedClaims();
        assertThat(claims.hasClaim(SecurityClaims.TENANT_ID)).isFalse();
        assertThat(claims.<List<String>>getClaim(SecurityClaims.ROLES)).containsExactly("STUDENT");
    }

    @Test
    void platformAccount_getsNoTenantIdClaim() {
        issuer.issue(account(null, Role.PLATFORM_ADMIN));

        JwtClaimsSet claims = issuedClaims();
        assertThat(claims.hasClaim(SecurityClaims.TENANT_ID)).isFalse();
        assertThat(claims.<List<String>>getClaim(SecurityClaims.ROLES)).containsExactly("PLATFORM_ADMIN");
    }

    @Test
    void rolesAreTheAccountsOwnRoles() {
        issuer.issue(account(UUID.randomUUID(), Role.EXAMINER, Role.PROCTOR));

        assertThat(issuedClaims().<List<String>>getClaim(SecurityClaims.ROLES))
                .containsExactlyInAnyOrder("EXAMINER", "PROCTOR");
    }

    @Test
    void expiryUsesTheConfiguredAccessLifetime() {
        issuer.issue(account(UUID.randomUUID(), Role.HOST_ADMIN));

        JwtClaimsSet claims = issuedClaims();
        assertThat(claims.getExpiresAt()).isEqualTo(claims.getIssuedAt().plusSeconds(900));
    }
}
