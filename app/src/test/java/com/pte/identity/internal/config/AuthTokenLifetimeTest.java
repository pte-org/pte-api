package com.pte.identity.internal.config;

import com.pte.TestClocks;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.domain.RefreshToken;
import com.pte.identity.internal.dto.request.auth.LoginRequest;
import com.pte.identity.internal.dto.request.auth.RefreshRequest;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
import com.pte.identity.internal.repository.RefreshTokenRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.security.AccessTokenIssuer;
import com.pte.identity.internal.security.TokenHasher;
import com.pte.identity.internal.service.AuthService;
import com.pte.identity.internal.service.LoginThrottle;
import com.pte.identity.internal.service.RefreshSession;
import com.pte.identity.internal.service.RefreshTokenService;
import com.pte.tenancy.TenancyService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthTokenLifetimeTest {

    private AuthTokenProperties configuredLifetimes() {
        AuthTokenProperties properties = new AuthTokenProperties();
        properties.setAccessTokenTtlSeconds(1200);
        properties.setRefreshTokenTtlSeconds(7200);
        return properties;
    }

    private User student() {
        User user = new User();
        user.setId(1L);
        user.setPublicId(UUID.randomUUID());
        user.setUsername("student-fixture");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(Role.STUDENT));
        user.setCredentialHash("fixture-hash");
        return user;
    }

    @Test
    void jwtExpiryUsesConfiguredAccessLifetime() {
        Clock clock = TestClocks.fixed();
        JwtEncoder encoder = mock(JwtEncoder.class);
        Jwt token = mock(Jwt.class);
        when(token.getTokenValue()).thenReturn("signed-fixture");
        when(encoder.encode(any())).thenReturn(token);
        AccessTokenIssuer issuer = new AccessTokenIssuer(encoder, configuredLifetimes(), clock);
        issuer.issue(student(), null);

        ArgumentCaptor<JwtEncoderParameters> encoded = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(encoder).encode(encoded.capture());
        assertThat(encoded.getValue().getClaims().getIssuedAt()).isEqualTo(clock.instant());
        assertThat(encoded.getValue().getClaims().getExpiresAt()).isEqualTo(clock.instant().plusSeconds(1200));
    }

    @Test
    void persistedRefreshExpiryUsesConfiguredRefreshLifetime() {
        Clock clock = TestClocks.fixed();
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        RefreshTokenService service = new RefreshTokenService(repository, new TokenHasher(), configuredLifetimes(), clock);
        service.issue(student(), null);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(clock.instant().plusSeconds(7200));
    }

    @Test
    void loginAndRefreshResponsesAdvertiseConfiguredAccessLifetime() {
        User user = student();
        UserRepository users = mock(UserRepository.class);
        OrganizationMemberRepository members = mock(OrganizationMemberRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        AccessTokenIssuer issuer = mock(AccessTokenIssuer.class);
        RefreshTokenService refresh = mock(RefreshTokenService.class);
        when(users.findByUsernameAndDeletedFalse(user.getUsername())).thenReturn(Optional.of(user));
        when(users.findByIdAndDeletedFalse(user.getId())).thenReturn(Optional.of(user));
        when(passwords.matches("fixture-password", "fixture-hash")).thenReturn(true);
        when(members.findByUser_IdAndDeletedFalse(user.getId())).thenReturn(List.of());
        when(issuer.issue(user, null)).thenReturn("access-fixture");
        when(refresh.issue(user, null)).thenReturn("refresh-fixture");
        when(refresh.consume("refresh-fixture")).thenReturn(new RefreshSession(user.getId(), null));
        AuthService auth = new AuthService(users, members, passwords, issuer, refresh,
                mock(TenancyService.class), mock(LoginThrottle.class), configuredLifetimes());

        assertThat(auth.login(new LoginRequest(user.getUsername(), "fixture-password")).expiresInSeconds()).isEqualTo(1200);
        assertThat(auth.refresh(new RefreshRequest("refresh-fixture")).expiresInSeconds()).isEqualTo(1200);
    }
}
