package com.pte.identity.internal.service;

import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.dto.request.LoginOrganizationOptionsRequest;
import com.pte.identity.internal.dto.request.LoginRequest;
import com.pte.identity.internal.dto.request.RefreshRequest;
import com.pte.identity.internal.dto.response.LoginOrganizationOptionResponse;
import com.pte.identity.internal.exception.InvalidLoginException;
import com.pte.identity.internal.exception.InvalidRefreshTokenException;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.security.AccessTokenIssuer;
import com.pte.tenancy.LoginOrganizationOption;
import com.pte.tenancy.TenancyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Phase 2: one account, several organizations. The token carries the organization chosen at login. */
@ExtendWith(MockitoExtension.class)
class AuthServiceMultiOrganizationTest {

    private static final String PASSWORD = "Local-Test-Pass-1";
    private static final UUID ORG_A = UUID.randomUUID();
    private static final UUID ORG_B = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationMemberRepository memberRepository;
    @Mock
    private AccessTokenIssuer accessTokenIssuer;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private TenancyService tenancyService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthService authService;
    private User host;
    private OrganizationMember inA;
    private OrganizationMember inB;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, memberRepository, passwordEncoder, accessTokenIssuer,
                refreshTokenService, tenancyService);
        host = new User();
        host.setId(7L);
        host.setPublicId(UUID.randomUUID());
        host.setUsername("host@school.vn");
        host.setStatus(UserStatus.ACTIVE);
        host.setCredentialHash(passwordEncoder.encode(PASSWORD));
        inA = member(host, ORG_A, Role.HOST_ADMIN);
        inB = member(host, ORG_B, Role.HOST_ADMIN);
    }

    @Test
    void credentialsMatchingAccountInTwoOrganizationsListBothAsLoginOptions() {
        when(userRepository.findByUsernameAndDeletedFalse("host@school.vn")).thenReturn(Optional.of(host));
        when(memberRepository.findByUser_IdAndDeletedFalse(7L)).thenReturn(List.of(inA, inB));
        when(tenancyService.findLoginOrganization(ORG_A)).thenReturn(Optional.of(
                new LoginOrganizationOption(ORG_A, "a", "School A", "HIGH_SCHOOL")));
        when(tenancyService.findLoginOrganization(ORG_B)).thenReturn(Optional.of(
                new LoginOrganizationOption(ORG_B, "b", "School B", "UNIVERSITY")));

        List<LoginOrganizationOptionResponse> options = authService.loginOrganizations(
                new LoginOrganizationOptionsRequest("host@school.vn", PASSWORD));

        assertThat(options).extracting(LoginOrganizationOptionResponse::tenantId).containsExactly(ORG_A, ORG_B);
    }

    @Test
    void wrongPasswordRevealsNoOrganizations() {
        when(userRepository.findByUsernameAndDeletedFalse("host@school.vn")).thenReturn(Optional.of(host));

        assertThatThrownBy(() -> authService.loginOrganizations(
                new LoginOrganizationOptionsRequest("host@school.vn", "wrong")))
                .isInstanceOf(InvalidLoginException.class);
    }

    @Test
    void loginWithAnOrganizationIssuesTokensForThatMembership() {
        when(userRepository.findByUsernameAndDeletedFalse("host@school.vn")).thenReturn(Optional.of(host));
        when(memberRepository.findByUser_IdAndOrganizationIdAndDeletedFalse(7L, ORG_B)).thenReturn(Optional.of(inB));
        when(accessTokenIssuer.issue(host, inB)).thenReturn("access-b");
        when(refreshTokenService.issue(host, ORG_B)).thenReturn("refresh-b");

        var response = authService.login(new LoginRequest("host@school.vn", PASSWORD, ORG_B));

        assertThat(response.accessToken()).isEqualTo("access-b");
        assertThat(response.refreshToken()).isEqualTo("refresh-b");
    }

    @Test
    void loginWithoutAnOrganizationIsRejectedWhenTheAccountBelongsToSeveral() {
        when(userRepository.findByUsernameAndDeletedFalse("host@school.vn")).thenReturn(Optional.of(host));
        when(memberRepository.findByUser_IdAndDeletedFalse(7L)).thenReturn(List.of(inA, inB));

        assertThatThrownBy(() -> authService.login(new LoginRequest("host@school.vn", PASSWORD)))
                .isInstanceOf(InvalidLoginException.class);
    }

    @Test
    void loginWithAnOrganizationTheUserDoesNotBelongToIsRejected() {
        UUID foreign = UUID.randomUUID();
        when(userRepository.findByUsernameAndDeletedFalse("host@school.vn")).thenReturn(Optional.of(host));
        when(memberRepository.findByUser_IdAndOrganizationIdAndDeletedFalse(7L, foreign)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("host@school.vn", PASSWORD, foreign)))
                .isInstanceOf(InvalidLoginException.class);
    }

    @Test
    void anAccountWithASingleMembershipLogsInWithoutPickingAnOrganization() {
        when(userRepository.findByUsernameAndDeletedFalse("host@school.vn")).thenReturn(Optional.of(host));
        when(memberRepository.findByUser_IdAndDeletedFalse(7L)).thenReturn(List.of(inA));
        when(accessTokenIssuer.issue(host, inA)).thenReturn("access-a");
        when(refreshTokenService.issue(host, ORG_A)).thenReturn("refresh-a");

        assertThat(authService.login(new LoginRequest("host@school.vn", PASSWORD)).accessToken())
                .isEqualTo("access-a");
    }

    @Test
    void refreshStaysInTheOrganizationTheTokenWasIssuedFor() {
        when(refreshTokenService.consume("raw")).thenReturn(new RefreshSession(7L, ORG_B));
        when(userRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(host));
        when(memberRepository.findByUser_IdAndOrganizationIdAndDeletedFalse(7L, ORG_B)).thenReturn(Optional.of(inB));
        when(accessTokenIssuer.issue(host, inB)).thenReturn("access-b2");
        when(refreshTokenService.issue(host, ORG_B)).thenReturn("refresh-b2");

        assertThat(authService.refresh(new RefreshRequest("raw")).accessToken()).isEqualTo("access-b2");
        verify(accessTokenIssuer).issue(eq(host), eq(inB));
    }

    @Test
    void refreshFailsWhenTheMembershipWasRemovedSinceTheTokenWasIssued() {
        when(refreshTokenService.consume("raw")).thenReturn(new RefreshSession(7L, ORG_B));
        when(userRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(host));
        when(memberRepository.findByUser_IdAndOrganizationIdAndDeletedFalse(7L, ORG_B)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("raw")))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(accessTokenIssuer, org.mockito.Mockito.never()).issue(any(), any());
    }

    private OrganizationMember member(User user, UUID organizationId, Role role) {
        OrganizationMember member = new OrganizationMember();
        member.setPublicId(UUID.randomUUID());
        member.setUser(user);
        member.setOrganizationId(organizationId);
        member.setRoles(Set.of(role));
        return member;
    }
}
