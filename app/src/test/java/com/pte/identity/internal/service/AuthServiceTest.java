package com.pte.identity.internal.service;

import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.dto.request.auth.LoginOrganizationOptionsRequest;
import com.pte.identity.internal.dto.request.auth.LoginRequest;
import com.pte.identity.internal.dto.request.auth.RefreshRequest;
import com.pte.identity.internal.dto.response.LoginOrganizationOptionResponse;
import com.pte.identity.internal.dto.response.TokenResponse;
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
import static org.mockito.Mockito.when;

/**
 * Login keys on the globally unique username. Platform roles sit on the account and sign in without an
 * organization; tenant roles sit on the membership. One test per role (not a parameterized/looped test) so a single
 * role silently falling through is its own failure, not hidden inside a shared assertion.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "Password123!";

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

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, memberRepository, passwordEncoder,
                accessTokenIssuer, refreshTokenService, tenancyService);
    }

    private User activeUser(Long id, Set<Role> roles, String username) {
        User user = new User();
        user.setId(id);
        user.setPublicId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(username);
        user.setFullName("Test User");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(roles);
        user.setCredentialHash(passwordEncoder.encode(PASSWORD));
        return user;
    }

    private OrganizationMember memberOf(User user, UUID organizationId, Role role) {
        OrganizationMember member = new OrganizationMember();
        member.setUser(user);
        member.setOrganizationId(organizationId);
        member.setStatus(UserStatus.ACTIVE);
        member.setRoles(Set.of(role));
        return member;
    }

    private void assertPlatformLoginSucceeds(Role role) {
        String username = role.name().toLowerCase() + "@platform.example";
        User user = activeUser(1L, Set.of(role), username);

        when(userRepository.findByUsernameAndDeletedFalse(username)).thenReturn(Optional.of(user));
        when(memberRepository.findByUser_IdAndDeletedFalse(1L)).thenReturn(List.of());
        when(accessTokenIssuer.issue(user, null)).thenReturn("access-token");
        when(refreshTokenService.issue(user, null)).thenReturn("refresh-token");

        assertBearerPair(authService.login(new LoginRequest(username, PASSWORD)));
    }

    private void assertMemberLoginSucceeds(Role role) {
        UUID organizationId = UUID.randomUUID();
        String username = role.name().toLowerCase() + "@tenant.example";
        User user = activeUser(1L, Set.of(), username);
        OrganizationMember member = memberOf(user, organizationId, role);

        when(userRepository.findByUsernameAndDeletedFalse(username)).thenReturn(Optional.of(user));
        when(memberRepository.findByUser_IdAndDeletedFalse(1L)).thenReturn(List.of(member));
        when(accessTokenIssuer.issue(user, member)).thenReturn("access-token");
        when(refreshTokenService.issue(user, organizationId)).thenReturn("refresh-token");

        assertBearerPair(authService.login(new LoginRequest(username, PASSWORD)));
    }

    private void assertBearerPair(TokenResponse response) {
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.mustChangePassword()).isFalse();
    }

    @Test
    void login_platformAdmin_succeeds() {
        assertPlatformLoginSucceeds(Role.PLATFORM_ADMIN);
    }

    @Test
    void login_platformAuthor_succeeds() {
        assertPlatformLoginSucceeds(Role.PLATFORM_AUTHOR);
    }

    @Test
    void login_hostAdmin_succeeds() {
        assertMemberLoginSucceeds(Role.HOST_ADMIN);
    }

    @Test
    void login_examiner_succeeds() {
        assertMemberLoginSucceeds(Role.EXAMINER);
    }

    @Test
    void login_proctor_succeeds() {
        assertMemberLoginSucceeds(Role.PROCTOR);
    }

    @Test
    void login_student_succeeds() {
        assertMemberLoginSucceeds(Role.STUDENT);
    }

    @Test
    void login_studentWithTemporaryPassword_returnsMustChangePasswordFlag() {
        UUID organizationId = UUID.randomUUID();
        String username = "school.abcdefgh";
        User user = activeUser(1L, Set.of(), username);
        user.setMustChangePassword(true);
        OrganizationMember member = memberOf(user, organizationId, Role.STUDENT);
        when(userRepository.findByUsernameAndDeletedFalse(username)).thenReturn(Optional.of(user));
        when(memberRepository.findByUser_IdAndDeletedFalse(1L)).thenReturn(List.of(member));
        when(accessTokenIssuer.issue(user, member)).thenReturn("access-token");
        when(refreshTokenService.issue(user, organizationId)).thenReturn("refresh-token");

        TokenResponse response = authService.login(new LoginRequest(username, PASSWORD));

        assertThat(response.mustChangePassword()).isTrue();
    }

    @Test
    void login_unknownUsername_throwsInvalidLogin() {
        when(userRepository.findByUsernameAndDeletedFalse("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", PASSWORD)))
                .isInstanceOf(InvalidLoginException.class);
    }

    @Test
    void login_wrongPassword_throwsInvalidLogin() {
        User user = activeUser(1L, Set.of(Role.PLATFORM_ADMIN), "admin@test.local");
        when(userRepository.findByUsernameAndDeletedFalse("admin@test.local")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin@test.local", "WrongPassword1")))
                .isInstanceOf(InvalidLoginException.class);
    }

    @Test
    void login_suspendedUser_throwsInvalidLoginWithoutCheckingPassword() {
        User user = activeUser(1L, Set.of(), "host@tenant.example");
        user.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByUsernameAndDeletedFalse("host@tenant.example")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("host@tenant.example", PASSWORD)))
                .isInstanceOf(InvalidLoginException.class);
    }

    @Test
    void login_archivedUser_throwsInvalidLogin() {
        when(userRepository.findByUsernameAndDeletedFalse("archived@example.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("archived@example.test", PASSWORD)))
                .isInstanceOf(InvalidLoginException.class);
    }

    @Test
    void refresh_archivedUser_throwsInvalidRefreshToken() {
        when(refreshTokenService.consume("refresh-token")).thenReturn(new RefreshSession(9L, null));
        when(userRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("refresh-token")))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void login_accountInSeveralOrganizations_requiresOrganizationSelection() {
        UUID firstOrganizationId = UUID.randomUUID();
        UUID secondOrganizationId = UUID.randomUUID();
        String username = "shared@tenant.example";
        User user = activeUser(1L, Set.of(), username);
        OrganizationMember first = memberOf(user, firstOrganizationId, Role.HOST_ADMIN);
        OrganizationMember second = memberOf(user, secondOrganizationId, Role.HOST_ADMIN);

        when(userRepository.findByUsernameAndDeletedFalse(username)).thenReturn(Optional.of(user));
        when(memberRepository.findByUser_IdAndDeletedFalse(1L)).thenReturn(List.of(first, second));
        assertThatThrownBy(() -> authService.login(new LoginRequest(username, PASSWORD)))
                .isInstanceOf(InvalidLoginException.class);

        when(memberRepository.findByUser_IdAndOrganizationIdAndDeletedFalse(1L, secondOrganizationId))
                .thenReturn(Optional.of(second));
        when(accessTokenIssuer.issue(user, second)).thenReturn("access-token");
        when(refreshTokenService.issue(user, secondOrganizationId)).thenReturn("refresh-token");

        TokenResponse response = authService.login(new LoginRequest(username, PASSWORD, secondOrganizationId));

        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    void loginOrganizations_returnsEveryOrganizationOfTheAuthenticatedAccount() {
        UUID firstOrganizationId = UUID.randomUUID();
        UUID secondOrganizationId = UUID.randomUUID();
        String username = "shared@tenant.example";
        User user = activeUser(1L, Set.of(), username);
        when(userRepository.findByUsernameAndDeletedFalse(username)).thenReturn(Optional.of(user));
        when(memberRepository.findByUser_IdAndDeletedFalse(1L)).thenReturn(List.of(
                memberOf(user, firstOrganizationId, Role.HOST_ADMIN),
                memberOf(user, secondOrganizationId, Role.STUDENT)));
        when(tenancyService.findLoginOrganization(firstOrganizationId)).thenReturn(Optional.of(
                new LoginOrganizationOption(firstOrganizationId, "alpha", "Alpha Center", "language-center")));
        when(tenancyService.findLoginOrganization(secondOrganizationId)).thenReturn(Optional.of(
                new LoginOrganizationOption(secondOrganizationId, "beta", "Beta School", "school")));

        List<LoginOrganizationOptionResponse> options = authService.loginOrganizations(
                new LoginOrganizationOptionsRequest(username, PASSWORD));

        assertThat(options).containsExactly(
                new LoginOrganizationOptionResponse(firstOrganizationId, "alpha", "Alpha Center", "language-center"),
                new LoginOrganizationOptionResponse(secondOrganizationId, "beta", "Beta School", "school"));
    }

    @Test
    void loginOrganizations_keepsSuspendedTenantSelectableForAuthentication() {
        UUID organizationId = UUID.randomUUID();
        String username = "shared@suspended.example";
        User user = activeUser(1L, Set.of(), username);
        when(userRepository.findByUsernameAndDeletedFalse(username)).thenReturn(Optional.of(user));
        when(memberRepository.findByUser_IdAndDeletedFalse(1L)).thenReturn(List.of(
                memberOf(user, organizationId, Role.HOST_ADMIN)));
        when(tenancyService.findLoginOrganization(organizationId)).thenReturn(Optional.of(
                new LoginOrganizationOption(organizationId, "suspended-school", "Suspended School", "school")));

        assertThat(authService.loginOrganizations(new LoginOrganizationOptionsRequest(username, PASSWORD)))
                .containsExactly(new LoginOrganizationOptionResponse(
                        organizationId, "suspended-school", "Suspended School", "school"));
    }

    @Test
    void login_suspendedMembership_isRejectedEvenWithTheRightPassword() {
        UUID organizationId = UUID.randomUUID();
        String username = "suspended@tenant.example";
        User user = activeUser(1L, Set.of(), username);
        OrganizationMember member = memberOf(user, organizationId, Role.STUDENT);
        member.suspend();
        when(userRepository.findByUsernameAndDeletedFalse(username)).thenReturn(Optional.of(user));
        when(memberRepository.findByUser_IdAndDeletedFalse(1L)).thenReturn(List.of(member));

        assertThatThrownBy(() -> authService.login(new LoginRequest(username, PASSWORD)))
                .isInstanceOf(InvalidLoginException.class);
    }

    @Test
    void login_chosenOrganizationWithSuspendedMembership_isRejected() {
        UUID organizationId = UUID.randomUUID();
        String username = "suspended@tenant.example";
        User user = activeUser(1L, Set.of(), username);
        OrganizationMember member = memberOf(user, organizationId, Role.STUDENT);
        member.suspend();
        when(userRepository.findByUsernameAndDeletedFalse(username)).thenReturn(Optional.of(user));
        when(memberRepository.findByUser_IdAndOrganizationIdAndDeletedFalse(1L, organizationId))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.login(new LoginRequest(username, PASSWORD, organizationId)))
                .isInstanceOf(InvalidLoginException.class);
    }

    @Test
    void refresh_suspendedMembership_isRejected() {
        UUID organizationId = UUID.randomUUID();
        User user = activeUser(9L, Set.of(), "suspended@tenant.example");
        OrganizationMember member = memberOf(user, organizationId, Role.STUDENT);
        member.suspend();
        when(refreshTokenService.consume("refresh-token")).thenReturn(new RefreshSession(9L, organizationId));
        when(userRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(user));
        when(memberRepository.findByUser_IdAndOrganizationIdAndDeletedFalse(9L, organizationId))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("refresh-token")))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void loginOrganizations_omitsOrganizationsWhereTheMembershipIsSuspended() {
        UUID activeOrganizationId = UUID.randomUUID();
        UUID suspendedOrganizationId = UUID.randomUUID();
        String username = "two@tenant.example";
        User user = activeUser(1L, Set.of(), username);
        OrganizationMember suspended = memberOf(user, suspendedOrganizationId, Role.STUDENT);
        suspended.suspend();
        when(userRepository.findByUsernameAndDeletedFalse(username)).thenReturn(Optional.of(user));
        when(memberRepository.findByUser_IdAndDeletedFalse(1L)).thenReturn(List.of(
                memberOf(user, activeOrganizationId, Role.HOST_ADMIN), suspended));
        when(tenancyService.findLoginOrganization(activeOrganizationId)).thenReturn(Optional.of(
                new LoginOrganizationOption(activeOrganizationId, "alpha", "Alpha Center", "school")));

        assertThat(authService.loginOrganizations(new LoginOrganizationOptionsRequest(username, PASSWORD)))
                .extracting(LoginOrganizationOptionResponse::tenantId).containsExactly(activeOrganizationId);
    }
}
